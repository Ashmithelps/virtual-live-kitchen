package com.virtualkitchen.payment;

/** Lifecycle of one payment attempt (a row in {@code payments}). */
public enum PaymentAttemptStatus {
    /** Provider order created; the customer may still pay (several tries are allowed). */
    CREATED,
    CAPTURED,
    FAILED,
    /** Refund decided (order rejected/cancelled); the provider hasn't accepted it yet. Retried by a job. */
    REFUND_REQUESTED,
    /** Provider accepted the refund; waiting for it to complete (refund.processed). */
    REFUND_PENDING,
    REFUNDED
}
