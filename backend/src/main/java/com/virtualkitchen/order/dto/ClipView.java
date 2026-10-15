package com.virtualkitchen.order.dto;

import java.time.Instant;

/**
 * A READY clip as seen in order responses (which chefs and the restaurant topic also get): no video link.
 * The customer, owner and admin fetch short-lived watch/download links from the clip playback endpoints.
 */
public record ClipView(double durationSeconds, String thumbnailUrl, Instant expiresAt) {
}
