package com.virtualkitchen.restaurant.table;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.table-session")
public record TableSessionProperties(@NotNull Duration ttl) {
}
