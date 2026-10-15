package com.virtualkitchen.tip;

public enum TipStatus {
    /** Gateway order created; the customer hasn't paid (yet). */
    CREATED,
    /** Payment captured; the transfer to the chef's linked account is on its way. */
    PAID,
    /** The gateway reports the money transferred to the chef's linked account. */
    TRANSFERRED,
    FAILED
}
