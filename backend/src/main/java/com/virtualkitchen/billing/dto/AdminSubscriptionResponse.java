package com.virtualkitchen.billing.dto;

import com.virtualkitchen.billing.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AdminSubscriptionResponse(UUID restaurantId, String restaurantName, SubscriptionStatus status, String planName, long pricePaise,
                                        Instant trialEndsAt, @Schema(nullable = true) Instant currentPeriodEnd,
                                        @Schema(nullable = true) Instant graceUntil, boolean paymentMethodReady,
                                        @Schema(nullable = true) String providerSubscriptionId) {
}
