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
        @Schema(nullable = true, description = "READY clips only; play via GET …/clips/{orderId}/playback") String thumbnailUrl,
        @Schema(nullable = true) String chefName,
        @Schema(nullable = true) String stationName,
        @Schema(description = "FAILED with raw footage still stored, so it can be rendered again") boolean retryable,
        @Schema(description = "Share-page views, one per visitor per day") long viewCount,
        @Schema(description = "Share links the customer created (including revoked ones)") long shareCount,
        @Schema(description = "The customer has a live share link") boolean shared,
        Instant createdAt,
        @Schema(nullable = true) Instant readyAt,
        @Schema(nullable = true) Instant expiresAt) {
}
