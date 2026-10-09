package com.virtualkitchen.restaurant;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "app.restaurant")
public record RestaurantProperties(@NotNull ZoneId timezone) {
}
