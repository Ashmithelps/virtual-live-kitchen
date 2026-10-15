package com.virtualkitchen.billing.dto;

import jakarta.validation.constraints.NotNull;

/** Mock gateway only: pretend Razorpay sent this subscription webhook. */
public record SimulateRequest(@NotNull Event event) {

    public enum Event { ACTIVATED, CHARGED, PENDING, HALTED, CANCELLED }
}
