package com.virtualkitchen.kitchen.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StationResponse(UUID id, String name, boolean deviceTokenIssued,
                              @Schema(nullable = true) Instant devicePairedAt,
                              @Schema(nullable = true, description = "Last API call from the paired phone; it heartbeats every 30 s")
                              Instant deviceLastSeenAt,
                              List<AssignedChef> chefs, Instant createdAt) {
}
