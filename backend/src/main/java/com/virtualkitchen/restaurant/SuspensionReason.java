package com.virtualkitchen.restaurant;

public enum SuspensionReason {
    /** An admin suspended the restaurant; only an admin reinstates it. */
    ADMIN,
    /** Billing: unpaid after the grace period. A successful payment reinstates it. */
    BILLING
}
