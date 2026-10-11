package com.virtualkitchen.kitchen.chef.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.virtualkitchen.kitchen.dto.StationRef;

import java.util.List;
import java.util.UUID;

/**
 * What a chef sees after login. When {@code consentRequired} is true the app must show the
 * consent screen (GET /api/chef/consent) before anything else.
 */
public record ChefSelfResponse(
        UUID id,
        String displayName,
        @Schema(nullable = true) String photoUrl,
        @Schema(nullable = true) String bio,
        @Schema(nullable = true) String specialty,
        UUID restaurantId,
        String restaurantName,
        boolean consentRequired,
        List<StationRef> stations) {
}
