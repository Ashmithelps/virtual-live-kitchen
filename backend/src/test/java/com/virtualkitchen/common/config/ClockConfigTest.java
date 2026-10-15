package com.virtualkitchen.common.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    private final Clock clock = new ClockConfig().clock();

    /** Postgres keeps microseconds; anything finer would be rounded on write and no longer match (CI on Linux). */
    @Test
    void nowHasNoSubMicrosecondDigits() {
        for (int i = 0; i < 1000; i++) {
            Instant now = clock.instant();
            assertThat(now.getNano() % 1_000).isZero();
        }
    }

    @Test
    void millisAndZoneWork() {
        long before = System.currentTimeMillis();
        assertThat(clock.millis()).isBetween(before - 5, System.currentTimeMillis() + 5);
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Z"));
        assertThat(clock.withZone(ZoneId.of("Asia/Kolkata")).instant().getNano() % 1_000).isZero();
    }
}
