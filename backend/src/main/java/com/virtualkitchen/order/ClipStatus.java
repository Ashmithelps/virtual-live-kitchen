package com.virtualkitchen.order;

/** What the customer can expect for this order's kitchen clip. Null until the order is READY. */
public enum ClipStatus {
    /** Footage requested from the station phone. */
    PENDING,
    /** Raw footage stored; waiting for the processing job. */
    UPLOADED,
    /** Being rendered (cut, timelapse, overlay, encode). */
    PROCESSING,
    /** Watchable: see OrderResponse.clip. */
    READY,
    /** Rendering failed after all retries. */
    FAILED,
    /** No clip (chef without consent, no recorder, no footage…). Never says why to the customer. */
    NOT_AVAILABLE,
    /** Deleted after the retention period. */
    EXPIRED
}
