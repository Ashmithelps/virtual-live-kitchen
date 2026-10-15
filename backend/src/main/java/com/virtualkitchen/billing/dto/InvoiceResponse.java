package com.virtualkitchen.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(UUID id, String number, String planName, @Schema(nullable = true) Instant periodStart,
                              @Schema(nullable = true) Instant periodEnd, long taxablePaise, long cgstPaise, long sgstPaise, long igstPaise,
                              long totalPaise, Instant issuedAt) {
}
