package com.virtualkitchen.auth.ratelimit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth.login-rate-limit")
public record LoginRateLimitProperties(@Min(1) int capacity, @NotNull Duration refillPeriod) {
}
