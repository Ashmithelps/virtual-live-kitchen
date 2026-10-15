package com.virtualkitchen.clip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Watch and download links for a READY clip, issued only to the order's customer, the restaurant owner and admins.
 * Links expire at {@code linksExpireAt} (10 minutes by default); fetch again for fresh ones.
 */
public record ClipPlaybackResponse(
        UUID orderId,
        int displayNumber,
        String restaurantName,
        @Schema(nullable = true) String chefName,
        double durationSeconds,
        String videoUrl,
        String thumbnailUrl,
        @Schema(description = "Saves the file as downloadFilename (Content-Disposition: attachment)") String downloadUrl,
        String downloadFilename,
        Instant linksExpireAt,
        @Schema(description = "When the clip itself is deleted") Instant clipExpiresAt,
        @Schema(description = "Views of the public share page, one per visitor per day") long viewCount,
        @Schema(nullable = true, description = "The customer's active share link (customer responses only)") ClipShareResponse share) {
}
