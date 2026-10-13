package com.virtualkitchen.clip;

/** Lifecycle of a customer clip (row in {@code clips}). */
public enum ClipState {
    /** Waiting for the station's footage, or queued for processing. */
    PENDING,
    PROCESSING,
    READY,
    /** Rendering failed after all retries. */
    FAILED,
    /** There will be no clip (no consent, no recorder, no footage). */
    NOT_AVAILABLE,
    /** Files deleted after the retention period. */
    EXPIRED
}
