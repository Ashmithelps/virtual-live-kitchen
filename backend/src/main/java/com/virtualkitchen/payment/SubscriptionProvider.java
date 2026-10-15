package com.virtualkitchen.payment;

import java.time.Instant;
import java.util.Map;

/**
 * Recurring billing (Razorpay Subscriptions). The owner authorises the mandate once in the gateway's checkout; the
 * gateway charges every period and reports through webhooks (activated, charged, pending, halted, cancelled).
 * Implementations make network calls; never hold database locks while calling them.
 */
public interface SubscriptionProvider {

    String name();

    /** A monthly gateway plan for this amount (GST included). Gateway plan amounts can't change: new price, new plan. */
    String createPlan(String name, long amountPaise, String description);

    /**
     * @param startAt first charge (e.g. the end of the free trial); null = now
     */
    NewSubscription createSubscription(String providerPlanId, Instant startAt, Map<String, String> notes);

    /** Checks the checkout handler's signature for this subscription and payment. */
    boolean verifyCheckout(String subscriptionId, String paymentId, String signature);

    /** Switches plan now (upgrade) or at the end of the current cycle (downgrade). */
    void changePlan(String subscriptionId, String providerPlanId, boolean atCycleEnd);

    void cancel(String subscriptionId, boolean atCycleEnd);

    /** Public key for the browser checkout; null when there is none. */
    default String publicKeyId() {
        return null;
    }

    record NewSubscription(String subscriptionId, String shortUrl, String status) {
    }
}
