package com.virtualkitchen.order.dto;

import java.time.Instant;

/** A READY clip. URLs are short-lived signed links; re-fetch the order for fresh ones. */
public record ClipView(double durationSeconds, String videoUrl, String thumbnailUrl, Instant expiresAt) {
}
