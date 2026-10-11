package com.virtualkitchen.order.dto;

import com.virtualkitchen.payment.CounterMethod;
import com.virtualkitchen.payment.PaymentAttemptStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** The most relevant payment attempt for an order (the settled one if any, else the latest). */
public record PaymentSummary(
        String provider,
        PaymentAttemptStatus status,
        long amountPaise,
        @Schema(nullable = true) CounterMethod counterMethod,
        @Schema(nullable = true) String failureReason) {
}
