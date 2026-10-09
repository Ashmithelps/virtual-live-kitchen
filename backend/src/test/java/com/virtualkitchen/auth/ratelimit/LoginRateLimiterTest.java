package com.virtualkitchen.auth.ratelimit;

import com.virtualkitchen.common.error.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    // 3 attempts, refilling one every 5 minutes.
    private final LoginRateLimiter limiter =
            new LoginRateLimiter(new LoginRateLimitProperties(3, Duration.ofMinutes(15)), clock);

    @Test
    void allowsUpToCapacityThenRejectsWithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            limiter.consume("+911111111111");
        }

        assertThatThrownBy(() -> limiter.consume("+911111111111"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfter())
                        .isEqualTo(Duration.ofMinutes(5)));
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.consume("a");
        }
        assertThatCode(() -> limiter.consume("b")).doesNotThrowAnyException();
    }

    @Test
    void refillsOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.consume("a");
        }
        clock.advance(Duration.ofMinutes(5));

        assertThatCode(() -> limiter.consume("a")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.consume("a")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void resetRestoresFullCapacity() {
        for (int i = 0; i < 3; i++) {
            limiter.consume("a");
        }
        limiter.reset("a");

        for (int i = 0; i < 3; i++) {
            limiter.consume("a");
        }
    }

    @Test
    void evictsOnlyFullyRefilledBuckets() {
        limiter.consume("a");
        clock.advance(Duration.ofMinutes(12));
        limiter.consume("b");

        // "a" has had 15 min to refill; "b" only 3 of the 5 it needs.
        clock.advance(Duration.ofMinutes(3));
        limiter.evictFullBuckets();

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
