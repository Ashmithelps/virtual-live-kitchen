package com.virtualkitchen.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ComplianceResponse(
        @Schema(nullable = true) String fssaiNumber,
        @Schema(nullable = true) String fssaiDocKey,
        @Schema(nullable = true, description = "Short-lived link to the uploaded licence") String fssaiDocUrl,
        @Schema(nullable = true) String legalName,
        @Schema(nullable = true) String gstin,
        @Schema(nullable = true) String billingStateCode) {
}
