package com.virtualkitchen.tip.dto;

import com.virtualkitchen.tip.PayoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** A chef's payout set-up, masked: never the full bank account or PAN. */
public record PayoutAccountResponse(
        PayoutStatus status,
        @Schema(nullable = true) String legalName,
        @Schema(nullable = true, description = "e.g. HDFC ••••1234") String bankAccountMasked,
        @Schema(nullable = true, description = "e.g. ••••••234F") String panMasked,
        @Schema(nullable = true, description = "Gateway's own status, e.g. under_review, needs_clarification") String providerStatus,
        @Schema(nullable = true) Instant updatedAt,
        @Schema(nullable = true) Instant activatedAt) {
}
