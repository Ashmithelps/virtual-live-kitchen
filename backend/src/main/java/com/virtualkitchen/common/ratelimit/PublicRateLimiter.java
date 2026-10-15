package com.virtualkitchen.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.PathContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory token buckets per (rule, client IP). Single node only, like the login limiter: a second backend
 * instance would need a shared store (e.g. Redis).
 */
@Component
public class PublicRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(PublicRateLimiter.class);

    private record CompiledRule(RateLimitProperties.Rule rule, Set<String> methods, List<PathPattern> paths, long fullNanos,
                                long costNanos) {
        boolean matches(String method, PathContainer path) {
            return methods.contains(method) && paths.stream().anyMatch(p -> p.matches(path));
        }
    }

    private final List<CompiledRule> rules;
    private final int maxTrackedClients;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public PublicRateLimiter(RateLimitProperties properties, Clock clock) {
        PathPatternParser parser = new PathPatternParser();
        this.rules = properties.rules().stream().map(r -> {
            long full = r.period().toNanos();
            return new CompiledRule(r, r.methods().stream().map(m -> m.toUpperCase(Locale.ROOT)).collect(Collectors.toSet()),
                    r.paths().stream().map(parser::parse).toList(), full, full / r.capacity());
        }).toList();
        this.maxTrackedClients = properties.maxTrackedClients();
        this.clock = clock;
    }

    /** The rule name that applies to this request, if any. */
    public Optional<String> ruleFor(String method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        return rules.stream().filter(r -> r.matches(method, container)).findFirst().map(r -> r.rule().name());
    }

    /** Takes one token; returns how long to wait when there is none (zero = allowed). */
    public Duration tryAcquire(String ruleName, String clientIp) {
        CompiledRule rule = rules.stream().filter(r -> r.rule().name().equals(ruleName)).findFirst().orElseThrow();
        long now = clock.millis() * 1_000_000L;
        String key = ruleName + '|' + clientIp;
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxTrackedClients) {
                evictFullBuckets();
                if (buckets.size() >= maxTrackedClients) {
                    // Fail open rather than grow without bound; a flood this wide needs edge protection (Cloudflare).
                    log.warn("Rate limiter is tracking {} clients; not limiting new ones", buckets.size());
                    return Duration.ZERO;
                }
            }
            bucket = buckets.computeIfAbsent(key, k -> new Bucket(rule.fullNanos(), now));
        }
        return bucket.take(rule.fullNanos(), rule.costNanos(), now);
    }

    /** Buckets that have refilled completely are the same as absent ones. */
    @Scheduled(fixedDelayString = "PT1M")
    public void evictFullBuckets() {
        long now = clock.millis() * 1_000_000L;
        buckets.values().removeIf(b -> b.isFull(now));
    }

    int trackedClients() {
        return buckets.size();
    }

    private static final class Bucket {
        private final long fullNanos;
        private long available;
        private long updatedAt;

        Bucket(long fullNanos, long now) {
            this.fullNanos = fullNanos;
            this.available = fullNanos;
            this.updatedAt = now;
        }

        synchronized Duration take(long full, long cost, long now) {
            refill(now);
            if (available >= cost) {
                available -= cost;
                return Duration.ZERO;
            }
            return Duration.ofNanos(cost - available);
        }

        synchronized boolean isFull(long now) {
            refill(now);
            return available >= fullNanos;
        }

        private void refill(long now) {
            if (now > updatedAt) {
                available = Math.min(fullNanos, available + (now - updatedAt));
                updatedAt = now;
            }
        }
    }
}
