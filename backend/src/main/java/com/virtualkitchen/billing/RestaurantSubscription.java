package com.virtualkitchen.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_subscriptions")
public class RestaurantSubscription {

    @Id
    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "pending_plan_id")
    private UUID pendingPlanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "trial_ends_at", nullable = false)
    private Instant trialEndsAt;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "grace_until")
    private Instant graceUntil;

    @Column(length = 20)
    private String provider;

    @Column(name = "provider_subscription_id", length = 64)
    private String providerSubscriptionId;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "payment_method_ready", nullable = false)
    private boolean paymentMethodReady;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RestaurantSubscription() {
    }

    RestaurantSubscription(UUID restaurantId, UUID planId, Instant trialEndsAt, Instant now) {
        this.restaurantId = restaurantId;
        this.planId = planId;
        this.status = SubscriptionStatus.TRIALING;
        this.trialEndsAt = trialEndsAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void attachGateway(String provider, String subscriptionId, String providerStatus, Instant now) {
        this.provider = provider;
        this.providerSubscriptionId = subscriptionId;
        this.providerStatus = providerStatus;
        this.paymentMethodReady = false;
        this.cancelledAt = null;
        this.updatedAt = now;
    }

    void paymentAuthorised(Instant now) {
        this.paymentMethodReady = true;
        this.providerStatus = "authenticated";
        if (status == SubscriptionStatus.CANCELLED) {
            this.status = SubscriptionStatus.TRIALING;
        }
        this.updatedAt = now;
    }

    void changePlan(UUID planId, Instant now) {
        this.planId = planId;
        this.pendingPlanId = null;
        this.updatedAt = now;
    }

    void schedulePlan(UUID planId, Instant now) {
        this.pendingPlanId = planId;
        this.updatedAt = now;
    }

    /** Activated or charged: paid up for this period. Applies a scheduled downgrade at the start of a new period. */
    void paid(Instant periodStart, Instant periodEnd, String providerStatus, Instant now) {
        if (pendingPlanId != null) {
            this.planId = pendingPlanId;
            this.pendingPlanId = null;
        }
        this.status = SubscriptionStatus.ACTIVE;
        if (periodStart != null) this.currentPeriodStart = periodStart;
        if (periodEnd != null) this.currentPeriodEnd = periodEnd;
        this.graceUntil = null;
        this.providerStatus = providerStatus;
        this.paymentMethodReady = true;
        this.updatedAt = now;
    }

    /** A failed charge or an unpaid trial: grace starts once and isn't extended by more failures. */
    void pastDue(Instant graceUntil, String providerStatus, Instant now) {
        if (status != SubscriptionStatus.SUSPENDED) {
            this.status = SubscriptionStatus.PAST_DUE;
        }
        if (this.graceUntil == null) {
            this.graceUntil = graceUntil;
        }
        if (providerStatus != null) this.providerStatus = providerStatus;
        this.updatedAt = now;
    }

    void suspended(Instant now) {
        this.status = SubscriptionStatus.SUSPENDED;
        this.updatedAt = now;
    }

    void cancelRequested(Instant now) {
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    void cancelled(String providerStatus, Instant now) {
        if (status != SubscriptionStatus.SUSPENDED) {
            this.status = SubscriptionStatus.CANCELLED;
        }
        if (cancelledAt == null) this.cancelledAt = now;
        this.providerStatus = providerStatus;
        this.paymentMethodReady = false;
        this.updatedAt = now;
    }

    public UUID getRestaurantId() { return restaurantId; }
    public UUID getPlanId() { return planId; }
    public UUID getPendingPlanId() { return pendingPlanId; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public Instant getGraceUntil() { return graceUntil; }
    public String getProvider() { return provider; }
    public String getProviderSubscriptionId() { return providerSubscriptionId; }
    public String getProviderStatus() { return providerStatus; }
    public boolean isPaymentMethodReady() { return paymentMethodReady; }
    public Instant getCancelledAt() { return cancelledAt; }
}
