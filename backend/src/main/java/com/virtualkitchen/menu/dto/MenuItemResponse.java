package com.virtualkitchen.menu.dto;

import java.util.UUID;

/** {@code imageUrl} is a temporary signed URL; re-fetch the menu rather than storing it. */
public record MenuItemResponse(
        UUID id,
        UUID categoryId,
        String name,
        String description,
        long pricePaise,
        boolean veg,
        String imageKey,
        String imageUrl,
        boolean available,
        int sortOrder) {
}
