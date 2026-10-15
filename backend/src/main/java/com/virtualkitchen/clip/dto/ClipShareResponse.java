package com.virtualkitchen.clip.dto;

import java.time.Instant;
import java.util.UUID;

/** The order's active public link, /c/{token}. */
public record ClipShareResponse(UUID id, String token, String url, Instant createdAt) {
}
