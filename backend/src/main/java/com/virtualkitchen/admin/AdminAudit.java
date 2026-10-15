package com.virtualkitchen.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.admin.dto.AuditEntryResponse;
import com.virtualkitchen.common.web.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only record of admin actions. Written in the same transaction as the action (so a rolled-back action leaves no
 * entry); views of private material (clips, licence documents) are recorded too.
 */
@Service
public class AdminAudit {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public AdminAudit(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID adminId, String action, String targetType, Object targetId, Map<String, ?> details) {
        String detailJson;
        try {
            detailJson = json.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException e) {
            detailJson = "{}";
        }
        jdbc.update("""
                INSERT INTO admin_audit_log (id, admin_id, action, target_type, target_id, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """, UUID.randomUUID(), adminId, action, targetType, targetId == null ? null : targetId.toString(), detailJson,
                Timestamp.from(clock.instant()));
    }

    /** Runs {@code action} and records it in one transaction: no entry without the change, no change without the entry. */
    @Transactional
    public <T> T audited(UUID adminId, String action, String targetType, Object targetId, Map<String, ?> details,
                         java.util.function.Supplier<T> work) {
        T result = work.get();
        record(adminId, action, targetType, targetId, details);
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> list(String action, int page, int size) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM admin_audit_log WHERE (CAST(? AS text) IS NULL OR action = ?)",
                Long.class, action, action);
        List<AuditEntryResponse> items = jdbc.query("""
                SELECT a.*, u.name AS admin_name FROM admin_audit_log a JOIN users u ON u.id = a.admin_id
                WHERE (CAST(? AS text) IS NULL OR a.action = ?)
                ORDER BY a.created_at DESC LIMIT ? OFFSET ?
                """, (rs, i) -> {
            Map<String, Object> details;
            try {
                details = json.readValue(rs.getString("details"), new TypeReference<>() { });
            } catch (JsonProcessingException e) {
                details = Map.of();
            }
            return new AuditEntryResponse(rs.getObject("id", UUID.class), rs.getObject("admin_id", UUID.class), rs.getString("admin_name"),
                    rs.getString("action"), rs.getString("target_type"), rs.getString("target_id"), details,
                    rs.getTimestamp("created_at").toInstant());
        }, action, action, size, (long) page * size);
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }
}
