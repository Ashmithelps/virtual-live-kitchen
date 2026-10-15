package com.virtualkitchen.tip.dto;

import com.virtualkitchen.tip.TipSource;
import com.virtualkitchen.tip.TipStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** A tip as the tipping customer sees it. */
public record TipResponse(UUID id, UUID chefId, String chefName, long amountPaise, TipStatus status, TipSource source,
                          Instant createdAt, @Schema(nullable = true) Instant paidAt) {
}
