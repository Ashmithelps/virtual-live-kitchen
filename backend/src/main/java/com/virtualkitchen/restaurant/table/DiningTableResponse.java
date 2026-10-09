package com.virtualkitchen.restaurant.table;

import java.time.Instant;
import java.util.UUID;

public record DiningTableResponse(UUID id, String label, String qrToken, Instant createdAt) {

    static DiningTableResponse from(DiningTable table) {
        return new DiningTableResponse(table.getId(), table.getLabel(), table.getQrToken(), table.getCreatedAt());
    }
}
