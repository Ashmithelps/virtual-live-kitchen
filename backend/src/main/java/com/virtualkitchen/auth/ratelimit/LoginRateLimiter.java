package com.virtualkitchen.auth.ratelimit;

import com.virtualkitchen.common.error.TooManyRequestsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token bucket per key (phone number). Each attempt costs one token; the bucket
 * refills linearly so that it is full again after {@code refillPeriod}. Single-node only —
 * replace with a shared store (e.g. Redis) before running multiple backend instances.
 */
@Component
public class LoginRateLimiter {

    /** Budget is tracked in nanoseconds of refill time (integer math, no float drift). */
    private final long fullNanos;
    private final long costNanos;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.fullNanos = properties.refillPeriod().toNanos();
        this.costNanos = fullNanos / properties.capacity();
        this.clock = clock;
    }

    /** @throws TooManyRequestsException if {@code key} has no attempts left */
    public void consume(String key) {
        Instant now = clock.instant();
        Duration wait = buckets.computeIfAbsent(key, k -> new Bucket(now)).tryConsume(now);
        if (!wait.isZero()) {
            throw new TooManyRequestsException("Too many login attempts. Try again later.", wait);
        }
    }

    /** Called after a successful login so a user's own typos don't count against them later. */
    public void reset(String key) {
        buckets.remove(key);
    }

    /** Drops buckets that have refilled completely; they are indistinguishable from absent ones. */
    @Scheduled(fixedDelayString = "PT5M")
    public void evictFullBuckets() {
        Instant now = clock.instant();
        buckets.values().removeIf(bucket -> bucket.isFull(now));
    }

    int trackedKeys() {
        return buckets.size();
    }

    private final class Bucket {

        private long availableNanos = fullNanos;
        private Instant lastRefill;

        Bucket(Instant now) {
            this.lastRefill = now;
        }

        /** @return {@link Duration#ZERO} if an attempt was granted, otherwise how long until one is available */
        synchronized Duration tryConsume(Instant now) {
            refill(now);
            if (availableNanos >= costNanos) {
                availableNanos -= costNanos;
                return Duration.ZERO;
            }
            return Duration.ofNanos(costNanos - availableNanos);
        }

        synchronized boolean isFull(Instant now) {
            refill(now);
            return availableNanos >= fullNanos;
        }

        private void refill(Instant now) {
            long elapsed = Duration.between(lastRefill, now).toNanos();
            if (elapsed > 0) {
                availableNanos = Math.min(fullNanos, availableNanos + elapsed);
                lastRefill = now;
            }
        }
    }
}
