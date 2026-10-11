package com.virtualkitchen.restaurant.table;

import com.virtualkitchen.menu.dto.MenuCategoryResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the customer app gets from a scanned QR code. Send {@code tableSessionToken} with dine-in
 * orders (logged in or guest); it pins the order to this table.
 */
public record TableLookupResponse(
        UUID restaurantId,
        String restaurantSlug,
        String restaurantName,
        String tableLabel,
        String tableSessionToken,
        Instant tableSessionExpiresAt,
        List<MenuCategoryResponse> menu) {

    @Override
    public String toString() {
        return "TableLookupResponse[restaurant=" + restaurantSlug + ", table=" + tableLabel + "]";
    }
}
