package com.virtualkitchen.tip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record FollowedChefResponse(UUID chefId, String displayName, @Schema(nullable = true) String photoUrl,
                                   @Schema(nullable = true) String specialty, UUID restaurantId, String restaurantName,
                                   String restaurantSlug, @Schema(description = "Payouts ACTIVE: tips can be offered") boolean tippable) {
}
