package com.virtualkitchen.tip.dto;

import com.virtualkitchen.tip.TipSource;
import com.virtualkitchen.tip.TipStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** A tip in the chef's own history (no customer details). */
public record ChefTipResponse(UUID id, int orderDisplayNumber, long amountPaise, long chefAmountPaise, TipStatus status,
                              TipSource source, Instant createdAt, @Schema(nullable = true) Instant paidAt,
                              @Schema(nullable = true) Instant transferredAt) {
}
