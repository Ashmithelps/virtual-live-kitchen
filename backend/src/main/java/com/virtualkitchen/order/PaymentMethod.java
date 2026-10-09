package com.virtualkitchen.order;

public enum PaymentMethod {
    /** Paid through the payment provider before the restaurant sees the order. */
    ONLINE,
    /** Dine-in only: settled at the table after eating. */
    PAY_LATER
}
