package com.virtualkitchen.restaurant.media;

import java.time.Instant;
import java.util.Map;

/** PUT the file bytes to {@code uploadUrl} with {@code headers}, then save {@code objectKey} on the menu item / chef. */
public record UploadResponse(String objectKey, String uploadUrl, String method, Map<String, String> headers,
                             Instant expiresAt) {
}
