package com.virtualkitchen.common.jobs;

import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** The Postgres queue itself (worker threads are off in the test profile; we claim by hand). */
class JobQueueIT extends IntegrationTest {

    @Autowired JobQueue queue;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentWorkersNeverClaimTheSameJob() throws Exception {
        String type = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 40; i++) queue.enqueue(type, Map.of("n", i), null);

        List<UUID> claimed = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int w = 0; w < 8; w++) {
                String id = "w" + w;
                workers.add(pool.submit(() -> {
                    start.await();
                    while (true) {
                        var job = queue.claim(id, List.of(type));
                        if (job.isEmpty()) return null;
                        claimed.add(job.get().id());
                        queue.succeed(job.get().id());
                    }
                }));
            }
            start.countDown();
            for (Future<?> f : workers) f.get();
        }
        assertThat(claimed).hasSize(40).doesNotHaveDuplicates();
        assertThat(count(type, "SUCCEEDED")).isEqualTo(40);
    }

    @Test
    void failedAttemptsBackOffThenGoDead() {
        String type = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        queue.enqueue(type, Map.of(), null);

        for (int attempt = 1; attempt <= 5; attempt++) {
            jdbc.update("update jobs set run_after = now() - interval '1 second' where type = ?", type);
            var job = queue.claim("w", List.of(type)).orElseThrow();
            assertThat(job.attempts()).isEqualTo(attempt);
            boolean dead = queue.fail(job, "boom " + attempt, false);
            assertThat(dead).isEqualTo(attempt == 5);
            if (!dead) {
                // Backoff: not claimable again straight away.
                assertThat(queue.claim("w", List.of(type))).isEmpty();
            }
        }
        assertThat(count(type, "DEAD")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select last_error from jobs where type = ?", String.class, type)).isEqualTo("boom 5");
    }

    @Test
    void permanentFailuresAreNotRetried() {
        String type = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        queue.enqueue(type, Map.of(), null);
        var job = queue.claim("w", List.of(type)).orElseThrow();
        assertThat(queue.fail(job, "bad input", true)).isTrue();
        assertThat(count(type, "DEAD")).isEqualTo(1);
    }

    @Test
    void dedupeKeyAllowsOneActiveJob() {
        String type = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        String key = "k:" + UUID.randomUUID();
        assertThat(queue.enqueue(type, Map.of(), key)).isTrue();
        assertThat(queue.enqueue(type, Map.of(), key)).isFalse();
        var job = queue.claim("w", List.of(type)).orElseThrow();
        assertThat(queue.enqueue(type, Map.of(), key)).as("still running").isFalse();
        queue.succeed(job.id());
        assertThat(queue.enqueue(type, Map.of(), key)).as("finished jobs don't block").isTrue();
    }

    @Test
    void jobsOfCrashedWorkersAreRecovered() {
        String type = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        queue.enqueue(type, Map.of(), null);
        queue.claim("crashed", List.of(type)).orElseThrow();
        jdbc.update("update jobs set locked_at = now() - interval '2 hours' where type = ?", type);

        queue.recoverExpiredLeases();

        assertThat(count(type, "QUEUED")).isEqualTo(1);
        assertThat(queue.claim("rescuer", List.of(type))).isPresent();
    }

    @Test
    void workersOnlySeeTheirTypes() {
        String mine = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        String other = "TEST_" + UUID.randomUUID().toString().substring(0, 8);
        queue.enqueue(other, Map.of(), null);
        assertThat(queue.claim("w", List.of(mine))).isEmpty();
        assertThat(queue.claim("w", Set.of())).isEmpty();
        assertThat(new HashSet<>(List.of(queue.claim("w", List.of(other)).orElseThrow().type()))).containsExactly(other);
    }

    private int count(String type, String status) {
        return jdbc.queryForObject("select count(*) from jobs where type = ? and status = ?", Integer.class, type, status);
    }
}
