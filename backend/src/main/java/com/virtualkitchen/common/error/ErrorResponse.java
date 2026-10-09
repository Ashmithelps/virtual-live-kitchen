package com.virtualkitchen.common.error;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/** The single JSON error shape returned by every endpoint (see CLAUDE.md). */
public record ErrorResponse(int status, String error, String message, Instant timestamp, String path) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, Instant.now(), path);
    }
}
