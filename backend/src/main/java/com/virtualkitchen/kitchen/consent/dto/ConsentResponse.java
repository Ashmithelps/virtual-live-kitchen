package com.virtualkitchen.kitchen.consent.dto;

import java.time.Instant;

/**
 * The text a chef must accept. To accept, POST back {@code version} and {@code textHash} exactly
 * as received; {@code accepted} is true only for the current version and not withdrawn.
 */
public record ConsentResponse(String version, String text, String textHash, boolean accepted, Instant acceptedAt) {
}
