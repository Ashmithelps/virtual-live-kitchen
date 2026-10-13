package com.virtualkitchen.clip.dto;

import java.time.Instant;
import java.util.Map;

/** PUT the chunk's bytes to {@code uploadUrl} with {@code headers}; report {@code objectKey} afterwards. */
public record ChunkUploadUrl(String chunkId, String objectKey, String uploadUrl, String method, Map<String, String> headers,
                             Instant expiresAt) {
}
