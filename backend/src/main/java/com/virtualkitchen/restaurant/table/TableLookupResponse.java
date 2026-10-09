package com.virtualkitchen.restaurant.table;

import java.util.UUID;

/** What a customer's app learns from scanning a table QR code. */
public record TableLookupResponse(UUID restaurantId, String restaurantSlug, String restaurantName, String tableLabel) {
}
