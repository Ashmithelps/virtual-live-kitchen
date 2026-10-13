package com.virtualkitchen.clip;

/** Internal reason a clip won't exist (kept for staff/audit; customers only see NOT_AVAILABLE). */
public enum NotAvailableReason {
    /** The chef had no valid recording consent when the order became READY. */
    NO_CONSENT,
    /** The station has no paired recording phone. */
    NO_DEVICE,
    /** The phone had no footage for the window (was off, or it was already rotated out). */
    NO_FOOTAGE,
    /** The phone never uploaded within the timeout. */
    UPLOAD_TIMEOUT
}
