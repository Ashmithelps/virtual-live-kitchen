package com.virtualkitchen.clip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** What anyone with a share link sees (no customer details). */
public record PublicClipResponse(
        String title,
        String restaurantName,
        String restaurantSlug,
        String restaurantCity,
        @Schema(description = "False when the restaurant isn't taking orders on the platform") boolean restaurantOrderable,
        @Schema(nullable = true) UUID chefId,
        @Schema(nullable = true) String chefName,
        double durationSeconds,
        String videoUrl,
        String thumbnailUrl,
        Instant recordedAt,
        Instant linksExpireAt) {
}
