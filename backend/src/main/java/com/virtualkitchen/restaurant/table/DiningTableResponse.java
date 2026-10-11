package com.virtualkitchen.restaurant.table;

import java.time.Instant;
import java.util.UUID;

/** {@code qrUrl} is exactly what the table's QR code encodes. */
public record DiningTableResponse(UUID id, String label, String qrToken, String qrUrl, Instant createdAt) {
}
