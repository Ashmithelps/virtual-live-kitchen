package com.virtualkitchen.clip.dto;

import com.virtualkitchen.clip.ClipState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One order's clip as the restaurant owner sees it (links are short-lived presigned URLs). */
public record OwnerClipResponse(
        UUID orderId,
        int displayNumber,
        LocalDate businessDate,
        ClipState status,
        @Schema(nullable = true) String failureReason,
        @Schema(nullable = true) Double durationSeconds,
        @Schema(nullable = true) String videoUrl,
        @Schema(nullable = true) String thumbnailUrl,
        @Schema(nullable = true) String chefName,
        @Schema(nullable = true) String stationName,
        @Schema(description = "FAILED with raw footage still stored, so it can be rendered again") boolean retryable,
        Instant createdAt,
        @Schema(nullable = true) Instant readyAt,
        @Schema(nullable = true) Instant expiresAt) {
}
