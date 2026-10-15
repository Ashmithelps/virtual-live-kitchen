package com.virtualkitchen.tip;

/** A chef's ability to receive tips. Tipping is offered only for ACTIVE. */
public enum PayoutStatus {
    NOT_SET_UP,
    /** Details submitted; the gateway is verifying KYC / the bank account. */
    PENDING_KYC,
    ACTIVE
}
