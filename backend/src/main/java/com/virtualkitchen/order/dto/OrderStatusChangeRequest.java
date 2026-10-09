package com.virtualkitchen.order.dto;

import com.virtualkitchen.order.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Moving to PREPARING requires {@code stationId}; owners must also give {@code chefId} (a chef's
 * own id is used when a chef makes the change). {@code reason} is stored for REJECTED / CANCELLED.
 */
public record OrderStatusChangeRequest(
        @NotNull OrderStatus status,
        UUID chefId,
        UUID stationId,
        @Size(max = 300) String reason) {
}
