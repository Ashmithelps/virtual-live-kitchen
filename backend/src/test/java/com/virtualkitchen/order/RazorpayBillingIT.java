package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.FakeRazorpay;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Razorpay Subscriptions through the real provider against the in-process fake API, driven by signed webhooks. */
@TestPropertySource(properties = {
        "app.payment.provider=razorpay",
        "app.payment.razorpay.key-id=" + FakeRazorpay.KEY_ID,
        "app.payment.razorpay.key-secret=" + FakeRazorpay.KEY_SECRET,
        "app.payment.razorpay.webhook-secret=" + FakeRazorpay.WEBHOOK_SECRET,
})
class RazorpayBillingIT extends IntegrationTest {

    static final FakeRazorpay RAZORPAY = new FakeRazorpay();

    @DynamicPropertySource
    static void razorpayUrl(DynamicPropertyRegistry registry) {
        registry.add("app.payment.razorpay.api-base-url", RAZORPAY::baseUrl);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    AuthClient client;
    OrderTestKitchen k;
    String base;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        k = new OrderTestKitchen(client);
        base = "/api/owner/restaurants/" + k.restaurantId + "/billing";
        client.send(PUT, "/api/owner/restaurants/" + k.restaurantId + "/compliance", k.owner,
                Map.of("legalName", "Test Kitchen Foods LLP", "gstin", "29ABCDE1234F1Z5")).andExpect(status().isOk());
    }

    @Test
    void mandateIsAuthorisedOnceAndWebhooksDriveCharges() throws Exception {
        JsonNode overview = client.body(client.send(GET, base, k.owner, null));
        String starter = overview.path("plan").path("id").asText();
        int plansBefore = RAZORPAY.calls("POST", "/v1/plans").size();

        JsonNode checkout = client.body(client.send(POST, base + "/checkout", k.owner, Map.of("planId", starter)).andExpect(status().isOk()));
        String sub = checkout.path("subscriptionId").asText();
        assertThat(checkout.path("keyId").asText()).isEqualTo(FakeRazorpay.KEY_ID);
        assertThat(checkout.path("shortUrl").asText()).startsWith("https://rzp.io/i/");

        // A monthly gateway plan at price + GST; the first charge waits for the trial to end.
        var planCalls = RAZORPAY.calls("POST", "/v1/plans");
        assertThat(planCalls.size()).isEqualTo(plansBefore + 1);
        JsonNode plan = planCalls.getLast().body();
        assertThat(plan.path("period").asText()).isEqualTo("monthly");
        assertThat(plan.path("item").path("amount").asLong()).isEqualTo(117_882);
        JsonNode created = RAZORPAY.calls("POST", "/v1/subscriptions").getLast().body();
        assertThat(created.path("start_at").asLong()).isEqualTo(Instant.parse(overview.path("trialEndsAt").asText()).getEpochSecond());
        assertThat(created.path("notes").path("restaurant_id").asText()).isEqualTo(k.restaurantId);

        // Checkout handler: signature HMAC(payment_id|subscription_id).
        client.send(POST, base + "/checkout/confirm", k.owner, Map.of("subscriptionId", sub, "paymentId", "pay_x", "signature", "forged"))
                .andExpect(status().isBadRequest());
        client.send(POST, base + "/checkout/confirm", k.owner, Map.of("subscriptionId", sub, "paymentId", "pay_x",
                "signature", FakeRazorpay.sign(FakeRazorpay.KEY_SECRET, "pay_x|" + sub))).andExpect(jsonPath("$.paymentMethodReady").value(true));

        // Gateway plan is reused for the next restaurant on the same price.
        OrderTestKitchen second = new OrderTestKitchen(client);
        client.send(PUT, "/api/owner/restaurants/" + second.restaurantId + "/compliance", second.owner,
                Map.of("legalName", "Second Kitchen", "billingStateCode", "29"));
        client.send(POST, "/api/owner/restaurants/" + second.restaurantId + "/billing/checkout", second.owner, Map.of("planId", starter))
                .andExpect(status().isOk());
        assertThat(RAZORPAY.calls("POST", "/v1/plans").size()).isEqualTo(plansBefore + 1);

        long start = Instant.now().getEpochSecond(), end = start + 30L * 86400;
        webhook("subscription.activated", sub, null, 0, start, end).andExpect(status().isOk());
        client.send(GET, base, k.owner, null).andExpect(jsonPath("$.status").value("ACTIVE"));
        webhook("subscription.charged", sub, "pay_m1", 117_882, start, end).andExpect(status().isOk());
        webhook("subscription.charged", sub, "pay_m1", 117_882, start, end).andExpect(status().isOk()); // redelivery
        JsonNode paid = client.body(client.send(GET, base, k.owner, null));
        assertThat(paid.path("invoices").size()).isEqualTo(1);
        assertThat(paid.path("invoices").get(0).path("cgstPaise").asLong()).isEqualTo(8_991);
        assertThat(Instant.parse(paid.path("currentPeriodEnd").asText()).getEpochSecond()).isEqualTo(end);

        // Upgrade now (PATCH schedule_change_at=now), then failures and cancellation by webhook.
        String growth = paid.path("plans").get(1).path("id").asText();
        client.send(PUT, base + "/plan", k.owner, Map.of("planId", growth)).andExpect(jsonPath("$.plan.code").value("growth"));
        JsonNode patch = RAZORPAY.calls("PATCH", "/v1/subscriptions/" + sub).getLast().body();
        assertThat(patch.path("schedule_change_at").asText()).isEqualTo("now");

        webhook("subscription.pending", sub, null, 0, start, end);
        client.send(GET, base, k.owner, null).andExpect(jsonPath("$.status").value("PAST_DUE")).andExpect(jsonPath("$.graceUntil").isNotEmpty());
        webhook("subscription.halted", sub, null, 0, start, end);
        webhook("subscription.charged", sub, "pay_m2", 294_882, start + 30L * 86400, end + 30L * 86400);
        client.send(GET, base, k.owner, null).andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.graceUntil").doesNotExist());

        client.send(POST, base + "/cancel", k.owner, null).andExpect(jsonPath("$.cancelRequested").value(true));
        assertThat(RAZORPAY.calls("POST", "/v1/subscriptions/" + sub + "/cancel").getLast().body().path("cancel_at_cycle_end").asInt()).isEqualTo(1);
        webhook("subscription.cancelled", sub, null, 0, start, end);
        client.send(GET, base, k.owner, null).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private ResultActions webhook(String event, String sub, String paymentId, long amount, long start, long end) throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("subscription", Map.of("entity", Map.of("id", sub, "status", event.substring(13), "current_start", start, "current_end", end)));
        if (paymentId != null) payload.put("payment", Map.of("entity", Map.of("id", paymentId, "amount", amount, "status", "captured")));
        String body = json.writeValueAsString(Map.of("entity", "event", "event", event, "payload", payload));
        return mvc.perform(post("/api/payments/razorpay/webhook").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Razorpay-Signature", FakeRazorpay.sign(FakeRazorpay.WEBHOOK_SECRET, body))
                .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID()));
    }
}
