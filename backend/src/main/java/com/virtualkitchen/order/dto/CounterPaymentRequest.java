package com.virtualkitchen.order.dto;

import com.virtualkitchen.payment.CounterMethod;
import jakarta.validation.constraints.NotNull;

/** Owner settles a pay-at-table bill in person. The amount is always the order's own total. */
public record CounterPaymentRequest(@NotNull CounterMethod method) {
}
