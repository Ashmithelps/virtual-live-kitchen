package com.virtualkitchen.hygiene.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record HygieneDisputeResponse(UUID id, String status, String reason, @Schema(nullable = true) String adminNote, Instant createdAt,
                                     @Schema(nullable = true) Instant resolvedAt) {
}
