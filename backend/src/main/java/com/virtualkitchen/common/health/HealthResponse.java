package com.virtualkitchen.common.health;

import java.time.Instant;

public record HealthResponse(String status, Instant time) {
}
