package com.virtualkitchen.billing.dto;

import com.virtualkitchen.billing.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** The owner's billing page. */
public record BillingOverviewResponse(
        SubscriptionStatus status,
        PlanResponse plan,
        @Schema(nullable = true) PlanResponse pendingPlan /* downgrade taking effect at the next charge */,
        Instant trialEndsAt,
        @Schema(nullable = true) Instant currentPeriodEnd,
        @Schema(nullable = true, description = "When the next charge is due (null when no payment method is set up)") Instant nextChargeAt,
        @Schema(nullable = true, description = "Listing is hidden after this if still unpaid") Instant graceUntil,
        boolean paymentMethodReady,
        boolean cancelRequested,
        @Schema(description = "Legal name and GST state are set (needed for invoices)") boolean billingDetailsComplete,
        List<PlanResponse> plans,
        List<InvoiceResponse> invoices) {
}
