package com.virtualkitchen.kitchen.chef.dto;

import com.virtualkitchen.common.hygiene.PublicHygieneScore;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** A chef's public profile (linked from shared clips). Only what the chef's restaurant shows customers anyway. */
public record PublicChefResponse(
        UUID id,
        String displayName,
        @Schema(nullable = true) String photoUrl,
        @Schema(nullable = true) String bio,
        @Schema(nullable = true) String specialty,
        String restaurantName,
        String restaurantSlug,
        String restaurantCity,
        long clipCount,
        @Schema(description = "Share-page views across the chef's clips") long totalViews,
        @Schema(description = "Number of tips received (never amounts)") long tipCount,
        long followerCount,
        @Schema(description = "Payouts set up: customers can tip this chef") boolean tippable,
        @Schema(nullable = true, description = "Only when public hygiene scores are on and enough of the chef's clips were scored")
        PublicHygieneScore hygiene) {
}
