package com.virtualkitchen.kitchen.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StationResponse(UUID id, String name, boolean deviceTokenIssued, List<AssignedChef> chefs, Instant createdAt) {
}
