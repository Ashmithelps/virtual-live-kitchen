package com.virtualkitchen.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Configuration
public class ClockConfig {

    /** Injected wherever "now" matters, so tests can control time. */
    @Bean
    public Clock clock() {
        return new MicrosecondClock(Clock.systemUTC());
    }

    /**
     * "Now" in whole microseconds, the precision of Postgres TIMESTAMPTZ. On Linux the JDK clock has nanoseconds, which
     * Postgres rounds on write, so a value sent to a client before commit (e.g. a footage window over STOMP) would
     * differ from the stored one. Not {@code Clock.tick(…, 1 µs)}: its {@code millis()} divides by zero for ticks
     * under a millisecond (JDK 21).
     */
    static final class MicrosecondClock extends Clock {

        private final Clock base;

        MicrosecondClock(Clock base) {
            this.base = base;
        }

        @Override
        public Instant instant() {
            return base.instant().truncatedTo(ChronoUnit.MICROS);
        }

        @Override
        public long millis() {
            return base.millis();
        }

        @Override
        public ZoneId getZone() {
            return base.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MicrosecondClock(base.withZone(zone));
        }
    }
}
