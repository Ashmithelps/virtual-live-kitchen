package com.virtualkitchen.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** {@code imageUrl} is a temporary signed URL; re-fetch the menu rather than storing it. */
public record MenuItemResponse(
        UUID id,
        UUID categoryId,
        String name,
        @Schema(nullable = true) String description,
        long pricePaise,
        boolean veg,
        @Schema(nullable = true) String imageKey,
        @Schema(nullable = true) String imageUrl,
        boolean available,
        int sortOrder) {
}
