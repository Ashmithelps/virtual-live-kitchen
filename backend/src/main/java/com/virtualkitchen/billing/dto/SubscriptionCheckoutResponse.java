package com.virtualkitchen.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Open the gateway checkout with {@code subscriptionId} (Razorpay: subscription_id), then POST its result to …/confirm. */
public record SubscriptionCheckoutResponse(String provider, @Schema(nullable = true) String keyId, String subscriptionId,
                                           @Schema(nullable = true, description = "Hosted authorisation page, as an alternative") String shortUrl,
                                           String merchantName, String description, long amountPaise,
                                           @Schema(nullable = true) String prefillName, @Schema(nullable = true) String prefillContact) {
}
