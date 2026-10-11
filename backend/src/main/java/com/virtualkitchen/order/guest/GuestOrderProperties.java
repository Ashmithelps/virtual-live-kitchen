package com.virtualkitchen.order.guest;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.guest-order")
public record GuestOrderProperties(@NotNull Duration tokenTtl) {
}
