package com.virtualkitchen.clip.dto;

import com.virtualkitchen.clip.ClipRequestStatus;
import com.virtualkitchen.clip.NotAvailableReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record ClipRequestResponse(UUID orderId, ClipRequestStatus status, @Schema(nullable = true) NotAvailableReason reason,
                                  int chunkCount) {
}
