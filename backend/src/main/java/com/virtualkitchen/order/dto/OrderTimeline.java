package com.virtualkitchen.order.dto;

import java.time.Instant;

/** UTC instants; null until the order reaches that state. */
public record OrderTimeline(Instant placedAt, Instant paidAt, Instant acceptedAt, Instant rejectedAt,
                            Instant preparingAt, Instant readyAt, Instant servedAt, Instant pickedUpAt,
                            Instant outForDeliveryAt, Instant deliveredAt, Instant cancelledAt, Instant refundedAt) {
}
