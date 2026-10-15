package com.virtualkitchen.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntryResponse(UUID id, UUID adminId, String adminName, String action, String targetType,
                                 @Schema(nullable = true) String targetId, Map<String, Object> details, Instant createdAt) {
}
