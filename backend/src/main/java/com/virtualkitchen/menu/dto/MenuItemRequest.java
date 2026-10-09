package com.virtualkitchen.menu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create/replace a menu item. {@code pricePaise} is in paise (₹249 = 24900).
 * {@code imageKey} comes from POST .../uploads (purpose MENU_ITEM_IMAGE); omit or null for no image.
 */
public record MenuItemRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull @Positive @Max(10_000_000) Long pricePaise,
        @NotNull Boolean veg,
        @Size(max = 300) String imageKey,
        Boolean available,
        @Min(0) @Max(10_000) Integer sortOrder) {
}
