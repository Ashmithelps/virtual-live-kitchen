package com.virtualkitchen.clip.dto;

import java.time.Instant;
import java.util.UUID;

/** Server-time window the station must upload footage for (PREPARING − 10 s … READY + 5 s). */
public record ClipWindowResponse(UUID clipRequestId, UUID orderId, int displayNumber, Instant windowStart, Instant windowEnd) {
}
