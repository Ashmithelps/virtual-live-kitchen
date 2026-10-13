package com.virtualkitchen.common.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Postgres-backed job queue. Enqueueing joins the caller's transaction, so a job is created if
 * and only if the business change commits. Claiming is a single statement using
 * {@code FOR UPDATE SKIP LOCKED}: concurrent workers (threads or instances) never get the same job
 * and never block each other.
 */
@Component
public class JobQueue {

    public record ClaimedJob(UUID id, String type, JsonNode payload, int attempts, int maxAttempts) {
    }

    public record DeadJob(UUID id, String type, JsonNode payload, String lastError) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final JobProperties properties;
    private final Clock clock;

    public JobQueue(JdbcTemplate jdbc, ObjectMapper json, JobProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return false if an active job with the same dedupe key already exists */
    public boolean enqueue(String type, Map<String, ?> payload, String dedupeKey) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.update("""
                INSERT INTO jobs (id, type, payload, dedupe_key, status, attempts, max_attempts, run_after, created_at, updated_at)
                VALUES (?, ?, ?::jsonb, ?, 'QUEUED', 0, ?, ?, ?, ?)
                ON CONFLICT (dedupe_key) WHERE status IN ('QUEUED', 'RUNNING') DO NOTHING
                """, UUID.randomUUID(), type, toJson(payload), dedupeKey, properties.maxAttempts(), now, now, now) == 1;
    }

    /**
     * Atomically takes the oldest due job of one of {@code types}, or nothing. Runs as its own
     * statement-level transaction. Workers only claim types they have handlers for.
     */
    public Optional<ClaimedJob> claim(String workerId, Collection<String> types) {
        if (types.isEmpty()) {
            return Optional.empty();
        }
        Timestamp now = Timestamp.from(clock.instant());
        List<ClaimedJob> claimed = jdbc.query("""
                UPDATE jobs j
                SET status = 'RUNNING', attempts = j.attempts + 1, locked_by = ?, locked_at = ?, updated_at = ?
                FROM (SELECT id FROM jobs
                      WHERE status = 'QUEUED' AND run_after <= ? AND type = ANY (?)
                      ORDER BY run_after
                      LIMIT 1
                      FOR UPDATE SKIP LOCKED) next
                WHERE j.id = next.id
                RETURNING j.id, j.type, j.payload::text AS payload, j.attempts, j.max_attempts
                """, (rs, i) -> new ClaimedJob(rs.getObject("id", UUID.class), rs.getString("type"),
                fromJson(rs.getString("payload")), rs.getInt("attempts"), rs.getInt("max_attempts")),
                workerId, now, now, now, types.toArray(String[]::new));
        return claimed.stream().findFirst();
    }

    public void succeed(UUID jobId) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                UPDATE jobs SET status = 'SUCCEEDED', locked_by = NULL, locked_at = NULL, last_error = NULL,
                       updated_at = ?, finished_at = ?
                WHERE id = ? AND status = 'RUNNING'
                """, now, now, jobId);
    }

    /**
     * Records a failed attempt: back to QUEUED with exponential backoff, or DEAD when permanent or
     * out of attempts.
     *
     * @return true if the job is now DEAD
     */
    public boolean fail(ClaimedJob job, String error, boolean permanent) {
        Instant now = clock.instant();
        boolean dead = permanent || job.attempts() >= job.maxAttempts();
        Instant retryAt = now.plus(properties.retryBaseDelay().multipliedBy(1L << Math.min(job.attempts() - 1, 16)));
        jdbc.update("""
                UPDATE jobs SET status = ?, run_after = ?, last_error = ?, locked_by = NULL, locked_at = NULL,
                       updated_at = ?, finished_at = ?
                WHERE id = ? AND status = 'RUNNING'
                """, dead ? "DEAD" : "QUEUED", Timestamp.from(dead ? now : retryAt), truncate(error),
                Timestamp.from(now), dead ? Timestamp.from(now) : null, job.id());
        return dead;
    }

    /**
     * Jobs whose worker vanished (crash, kill, deploy) keep status RUNNING forever; after the
     * lease they're re-queued, or declared DEAD if that was their last attempt.
     *
     * @return the jobs that just became DEAD (their handlers' onExhausted must run)
     */
    public List<DeadJob> recoverExpiredLeases() {
        Instant now = clock.instant();
        Timestamp cutoff = Timestamp.from(now.minus(properties.lease()));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                UPDATE jobs
                SET status = CASE WHEN attempts >= max_attempts THEN 'DEAD' ELSE 'QUEUED' END,
                    run_after = ?, locked_by = NULL, locked_at = NULL, updated_at = ?,
                    last_error = 'Worker lease expired (worker stopped or crashed)',
                    finished_at = CASE WHEN attempts >= max_attempts THEN CAST(? AS timestamptz) END
                WHERE status = 'RUNNING' AND locked_at < ?
                RETURNING id, type, payload::text AS payload, status, last_error
                """, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), cutoff);
        return rows.stream()
                .filter(r -> "DEAD".equals(r.get("status")))
                .map(r -> new DeadJob((UUID) r.get("id"), (String) r.get("type"), fromJson((String) r.get("payload")),
                        (String) r.get("last_error")))
                .toList();
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Job payload is not serialisable", e);
        }
    }

    private JsonNode fromJson(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt job payload", e);
        }
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 4000 ? error : error.substring(0, 4000);
    }
}
