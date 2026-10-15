package com.virtualkitchen.common.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs {@code app.jobs.worker-threads} polling threads in this instance (0 = none). Moving the
 * work to a dedicated instance later means running the same app with threads > 0 there and 0 on
 * the API nodes; the queue is shared through Postgres.
 */
@Component
public class JobWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobQueue queue;
    private final JobProperties properties;
    private final Map<String, JobHandler> handlers;
    private final String instanceId = ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID().toString().substring(0, 8);
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    public JobWorker(JobQueue queue, JobProperties properties, List<JobHandler> handlers) {
        this.queue = queue;
        this.properties = properties;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
    }

    @Override
    public void start() {
        running = true;
        Map<String, List<String>> typesByLane = handlers.values().stream()
                .collect(Collectors.groupingBy(JobHandler::lane, Collectors.mapping(JobHandler::type, Collectors.toList())));
        typesByLane.forEach((lane, types) -> {
            int count = properties.threadsFor(lane);
            for (int i = 0; i < count; i++) {
                Thread thread = Thread.ofPlatform().name("job-" + lane + "-" + i).daemon(true).unstarted(() -> loop(types));
                threads.add(thread);
                thread.start();
            }
            if (count > 0) {
                log.info("Started {} job worker thread(s) in lane '{}' for {}", count, lane, types);
            }
        });
    }

    @Override
    public void stop() {
        running = false;
        threads.forEach(Thread::interrupt);
        for (Thread thread : threads) {
            try {
                thread.join(5_000); // a job still running is re-queued by the lease reaper later
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        threads.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Re-queues jobs abandoned by crashed workers. Runs on every instance; the UPDATE is idempotent. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void recoverAbandonedJobs() {
        for (JobQueue.DeadJob dead : queue.recoverExpiredLeases()) {
            exhausted(dead.type(), dead.payload(), dead.lastError());
        }
    }

    /** Takes and runs at most one job of any type; returns false if there was none. Used by tests. */
    public boolean runOnce(String workerId) {
        return runOnce(workerId, handlers.keySet());
    }

    /** Takes and runs at most one job of the given types; returns false if there was none. */
    public boolean runOnce(String workerId, java.util.Collection<String> types) {
        Optional<JobQueue.ClaimedJob> claimed = queue.claim(workerId, types);
        if (claimed.isEmpty()) {
            return false;
        }
        JobQueue.ClaimedJob job = claimed.get();
        JobHandler handler = handlers.get(job.type());
        try {
            if (handler == null) {
                throw new PermanentJobFailure("No handler for job type " + job.type());
            }
            handler.handle(job.payload());
            queue.succeed(job.id());
        } catch (Exception e) {
            boolean permanent = e instanceof PermanentJobFailure;
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            boolean dead = queue.fail(job, error, permanent);
            if (dead) {
                log.warn("Job {} ({}) gave up after attempt {}: {}", job.id(), job.type(), job.attempts(), error);
                exhausted(job.type(), job.payload(), error);
            } else {
                log.info("Job {} ({}) attempt {} failed, will retry: {}", job.id(), job.type(), job.attempts(), error);
            }
        }
        return true;
    }

    private void loop(List<String> types) {
        String workerId = instanceId + "#" + Thread.currentThread().getName();
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                if (!runOnce(workerId, types)) {
                    Thread.sleep(properties.pollInterval());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                // DB briefly unavailable etc.: back off and keep the worker alive.
                log.warn("Job worker error: {}", e.getMessage());
                try {
                    Thread.sleep(properties.pollInterval());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void exhausted(String type, com.fasterxml.jackson.databind.JsonNode payload, String error) {
        JobHandler handler = handlers.get(type);
        if (handler == null) {
            return;
        }
        try {
            handler.onExhausted(payload, error);
        } catch (RuntimeException e) {
            log.error("onExhausted for {} failed: {}", type, e.getMessage(), e);
        }
    }
}
