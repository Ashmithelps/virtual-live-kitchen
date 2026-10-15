package com.virtualkitchen.payment.razorpay;

import com.virtualkitchen.payment.SubscriptionProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Razorpay Subscriptions: monthly plans, a subscription per restaurant authorised once in Checkout, charges reported by
 * webhooks (subscription.activated / charged / pending / halted / cancelled).
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpaySubscriptionProvider implements SubscriptionProvider {

    /** Long enough to be open-ended (Razorpay needs a count): 10 years of monthly charges. */
    private static final int TOTAL_COUNT = 120;

    private final RazorpayProperties properties;
    private final RazorpayClient client;

    public RazorpaySubscriptionProvider(RazorpayProperties properties) {
        this.properties = properties;
        this.client = new RazorpayClient(properties);
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public String createPlan(String name, long amountPaise, String description) {
        return client.createPlan(name, amountPaise, description).id();
    }

    @Override
    public NewSubscription createSubscription(String providerPlanId, Instant startAt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan_id", providerPlanId);
        body.put("total_count", TOTAL_COUNT);
        body.put("quantity", 1);
        body.put("customer_notify", 1);
        if (startAt != null && startAt.isAfter(Instant.now().plusSeconds(60))) {
            body.put("start_at", startAt.getEpochSecond());
        }
        body.put("notes", notes);
        RazorpayClient.RzpSubscription s = client.createSubscription(body);
        return new NewSubscription(s.id(), s.shortUrl(), s.status());
    }

    @Override
    public boolean verifyCheckout(String subscriptionId, String paymentId, String signature) {
        return RazorpaySignatures.subscriptionCheckoutValid(subscriptionId, paymentId, signature, properties.keySecret());
    }

    @Override
    public void changePlan(String subscriptionId, String providerPlanId, boolean atCycleEnd) {
        client.updateSubscription(subscriptionId, Map.of("plan_id", providerPlanId, "schedule_change_at", atCycleEnd ? "cycle_end" : "now",
                "customer_notify", 1));
    }

    @Override
    public void cancel(String subscriptionId, boolean atCycleEnd) {
        client.cancelSubscription(subscriptionId, atCycleEnd);
    }

    @Override
    public String publicKeyId() {
        return properties.keyId();
    }
}
