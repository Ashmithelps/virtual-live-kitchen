package com.virtualkitchen.payment;

import com.virtualkitchen.common.crypto.SecureTokens;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Development stand-in for Razorpay Subscriptions: everything succeeds and no webhooks arrive. Admins drive charges,
 * failures and cancellations with the billing "simulate" endpoint (mock provider only).
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock")
public class MockSubscriptionProvider implements SubscriptionProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String createPlan(String name, long amountPaise, String description) {
        return "plan_mock_" + SecureTokens.random(9);
    }

    @Override
    public NewSubscription createSubscription(String providerPlanId, Instant startAt, Map<String, String> notes) {
        return new NewSubscription("sub_mock_" + SecureTokens.random(9), null, "created");
    }

    @Override
    public boolean verifyCheckout(String subscriptionId, String paymentId, String signature) {
        return !paymentId.startsWith("fail");
    }

    @Override
    public void changePlan(String subscriptionId, String providerPlanId, boolean atCycleEnd) {
    }

    @Override
    public void cancel(String subscriptionId, boolean atCycleEnd) {
    }
}
