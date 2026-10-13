package com.virtualkitchen.order;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised (synchronously, inside the transaction) when an order moves to READY: the moment the
 * footage window [preparingAt, readyAt] is known. The clip module decides whether to request it.
 */
public record OrderReadyEvent(UUID orderId, UUID restaurantId, UUID stationId, UUID chefId, int displayNumber,
                              Instant preparingAt, Instant readyAt) {
}
