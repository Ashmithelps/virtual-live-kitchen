package com.virtualkitchen.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the gateway's checkout hands back to the browser (Razorpay: razorpay_order_id/payment_id/signature). */
public record ConfirmPaymentRequest(
        @NotBlank @Size(max = 100) String providerOrderId,
        @NotBlank @Size(max = 100) String providerPaymentId,
        @Size(max = 512) String signature) {
}
