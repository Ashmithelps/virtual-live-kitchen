package com.virtualkitchen.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRateLimiterTest {

    private Instant now = Instant.parse("2026-10-15T10:00:00Z");
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };

    private PublicRateLimiter limiter(int maxClients) {
        return new PublicRateLimiter(new RateLimitProperties(true, "", maxClients, List.of(
                new RateLimitProperties.Rule("auth", List.of("post"), List.of("/api/auth/login"), 2, Duration.ofMinutes(1)),
                new RateLimitProperties.Rule("read", List.of("GET"), List.of("/api/restaurants", "/api/restaurants/**", "/api/public/**"), 3,
                        Duration.ofMinutes(1)))), clock);
    }

    @Test
    void matchesTheFirstRuleByMethodAndPath() {
        PublicRateLimiter l = limiter(1000);
        assertThat(l.ruleFor("POST", "/api/auth/login")).contains("auth");
        assertThat(l.ruleFor("GET", "/api/auth/login")).isEmpty();
        assertThat(l.ruleFor("GET", "/api/restaurants")).contains("read");
        assertThat(l.ruleFor("GET", "/api/restaurants/dev-spice-kitchen")).contains("read");
        assertThat(l.ruleFor("GET", "/api/public/clips/abc")).contains("read");
        assertThat(l.ruleFor("POST", "/api/payments/razorpay/webhook")).isEmpty();
        assertThat(l.ruleFor("GET", "/api/owner/restaurants")).isEmpty();
    }

    @Test
    void limitsPerClientAndRefillsEvenly() {
        PublicRateLimiter l = limiter(1000);
        for (int i = 0; i < 3; i++) assertThat(l.tryAcquire("read", "1.1.1.1")).isZero();
        Duration wait = l.tryAcquire("read", "1.1.1.1");
        assertThat(wait).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(20));
        // Another client and another rule have their own buckets.
        assertThat(l.tryAcquire("read", "2.2.2.2")).isZero();
        assertThat(l.tryAcquire("auth", "1.1.1.1")).isZero();
        // One token comes back every period / capacity = 20 s.
        now = now.plusSeconds(20);
        assertThat(l.tryAcquire("read", "1.1.1.1")).isZero();
        assertThat(l.tryAcquire("read", "1.1.1.1")).isPositive();
    }

    @Test
    void forgetsRefilledClientsAndFailsOpenWhenFull() {
        PublicRateLimiter l = limiter(1000);
        for (int i = 0; i < 1000; i++) l.tryAcquire("read", "10.0.0." + i);
        assertThat(l.trackedClients()).isEqualTo(1000);
        assertThat(l.tryAcquire("read", "9.9.9.9")).isZero();
        assertThat(l.trackedClients()).isEqualTo(1000);
        now = now.plusSeconds(61);
        l.evictFullBuckets();
        assertThat(l.trackedClients()).isZero();
    }

    @Test
    void trustsXClientIpOnlyWithTheProxySecret() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("76.76.21.21");
        r.addHeader(ClientIp.CLIENT_IP_HEADER, "49.36.1.2");
        assertThat(ClientIp.of(r, "")).isEqualTo("76.76.21.21");
        assertThat(ClientIp.of(r, "s3cret")).isEqualTo("76.76.21.21");
        r.addHeader(ClientIp.PROXY_SECRET_HEADER, "wrong");
        assertThat(ClientIp.of(r, "s3cret")).isEqualTo("76.76.21.21");
        MockHttpServletRequest ok = new MockHttpServletRequest();
        ok.setRemoteAddr("76.76.21.21");
        ok.addHeader(ClientIp.PROXY_SECRET_HEADER, "s3cret");
        ok.addHeader(ClientIp.CLIENT_IP_HEADER, "2401:4900::1");
        assertThat(ClientIp.of(ok, "s3cret")).isEqualTo("2401:4900::1");
        MockHttpServletRequest junk = new MockHttpServletRequest();
        junk.setRemoteAddr("76.76.21.21");
        junk.addHeader(ClientIp.PROXY_SECRET_HEADER, "s3cret");
        junk.addHeader(ClientIp.CLIENT_IP_HEADER, "<script>");
        assertThat(ClientIp.of(junk, "s3cret")).isEqualTo("76.76.21.21");
    }
}
