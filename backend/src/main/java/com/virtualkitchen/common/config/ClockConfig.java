package com.virtualkitchen.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injected wherever "now" matters, so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
