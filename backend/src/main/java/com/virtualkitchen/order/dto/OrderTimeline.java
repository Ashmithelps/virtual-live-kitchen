package com.virtualkitchen.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** UTC instants; null until the order reaches that state. */
public record OrderTimeline(Instant placedAt, @Schema(nullable = true) Instant paidAt, @Schema(nullable = true) Instant acceptedAt, @Schema(nullable = true) Instant rejectedAt,
                            @Schema(nullable = true) Instant preparingAt, @Schema(nullable = true) Instant readyAt, @Schema(nullable = true) Instant servedAt, @Schema(nullable = true) Instant pickedUpAt,
                            @Schema(nullable = true) Instant outForDeliveryAt, @Schema(nullable = true) Instant deliveredAt, @Schema(nullable = true) Instant cancelledAt, @Schema(nullable = true) Instant refundedAt) {
}
