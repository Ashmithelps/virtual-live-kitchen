package com.virtualkitchen.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything the browser needs to open the gateway's checkout. For Razorpay: pass
 * {@code keyId}, {@code providerOrderId} (as order_id), {@code amountPaise}, {@code currency}
 * to Razorpay Checkout, then POST its response to .../payment/confirm.
 */
public record PaymentCheckoutResponse(
        String provider,
        String providerOrderId,
        @Schema(nullable = true) String keyId,
        long amountPaise,
        String currency,
        String merchantName,
        String description,
        @Schema(nullable = true) String prefillName,
        @Schema(nullable = true) String prefillContact) {
}
