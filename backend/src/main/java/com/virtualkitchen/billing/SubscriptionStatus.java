package com.virtualkitchen.billing;

public enum SubscriptionStatus {
    /** Free trial; listing visible. */
    TRIALING,
    ACTIVE,
    /** A charge failed (or the trial ended unpaid): listing still visible until {@code graceUntil}. */
    PAST_DUE,
    /** Unpaid past grace (or cancelled past the paid period): listing hidden, data kept. */
    SUSPENDED,
    /** Cancelled; runs until the end of the paid period. */
    CANCELLED
}
