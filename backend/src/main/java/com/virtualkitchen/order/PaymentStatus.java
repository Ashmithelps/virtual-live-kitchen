package com.virtualkitchen.order;

import java.util.EnumSet;
import java.util.Set;

public enum PaymentStatus {
    /** Online payment not completed yet; the restaurant cannot see the order. */
    PENDING,
    PAID,
    /** Last online attempt failed; the customer may retry. */
    FAILED,
    PAY_LATER,
    REFUNDED;

    /** Orders with these payment states are shown to the restaurant. */
    public static final Set<PaymentStatus> VISIBLE_TO_RESTAURANT = EnumSet.of(PAID, PAY_LATER, REFUNDED);
}
