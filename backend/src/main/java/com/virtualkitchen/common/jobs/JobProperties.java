package com.virtualkitchen.common.jobs;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.jobs")
public record JobProperties(
        @Min(0) int workerThreads,
        @NotNull Duration pollInterval,
        @NotNull Duration lease,
        @Min(1) int maxAttempts,
        @NotNull Duration retryBaseDelay) {
}
