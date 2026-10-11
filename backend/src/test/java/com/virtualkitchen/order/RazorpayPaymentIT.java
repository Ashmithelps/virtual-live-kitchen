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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real RazorpayPaymentProvider against an in-process fake of the Razorpay API. */
@TestPropertySource(properties = {
        "app.payment.provider=razorpay",
        "app.payment.razorpay.key-id=" + FakeRazorpay.KEY_ID,
        "app.payment.razorpay.key-secret=" + FakeRazorpay.KEY_SECRET,
        "app.payment.razorpay.webhook-secret=" + FakeRazorpay.WEBHOOK_SECRET,
})
class RazorpayPaymentIT extends IntegrationTest {

    static final FakeRazorpay RAZORPAY = new FakeRazorpay();
    static final String WEBHOOK = "/api/payments/razorpay/webhook";
    /** 2 × 24900 + 9900 = 59700 subtotal + 2985 GST (5%) */
    static final long TOTAL = 62685;

    @DynamicPropertySource
    static void razorpayUrl(DynamicPropertyRegistry registry) {
        registry.add("app.payment.razorpay.api-base-url", RAZORPAY::baseUrl);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderPaymentService paymentService;

    AuthClient client;
    OrderTestKitchen k;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        k = new OrderTestKitchen(client);
        RAZORPAY.refundsFail = false;
        RAZORPAY.refundResponsesLost = false;
    }

    // ── checkout ─────────────────────────────────────────────────────────────

    @Test
    void checkoutCreatesARazorpayOrderForTheServerComputedTotal() throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();
        int before = RAZORPAY.calls("POST", "/v1/orders").size();

        JsonNode checkout = client.body(k.startPayment(id).andExpect(status().isOk()));
        assertThat(checkout.path("provider").asText()).isEqualTo("razorpay");
        assertThat(checkout.path("keyId").asText()).isEqualTo(FakeRazorpay.KEY_ID);
        assertThat(checkout.path("amountPaise").asLong()).isEqualTo(TOTAL);
        assertThat(checkout.path("currency").asText()).isEqualTo("INR");
        assertThat(checkout.path("providerOrderId").asText()).startsWith("order_fake");

        var created = RAZORPAY.calls("POST", "/v1/orders");
        assertThat(created).hasSize(before + 1);
        JsonNode sent = created.getLast().body();
        assertThat(sent.path("amount").asLong()).isEqualTo(TOTAL);
        assertThat(sent.path("currency").asText()).isEqualTo("INR");
        assertThat(sent.path("receipt").asText()).isEqualTo("o_" + id);

        // Reopening checkout reuses the same Razorpay order.
        JsonNode again = client.body(k.startPayment(id).andExpect(status().isOk()));
        assertThat(again.path("providerOrderId").asText()).isEqualTo(checkout.path("providerOrderId").asText());
        assertThat(RAZORPAY.calls("POST", "/v1/orders")).hasSize(before + 1);
    }

    @Test
    void validCheckoutSignatureMarksTheOrderPaid() throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();
        String rzpOrder = providerOrder(id);
        RAZORPAY.addPayment("pay_ok1", rzpOrder, TOTAL, "captured");

        confirm(id, rzpOrder, "pay_ok1", FakeRazorpay.sign(FakeRazorpay.KEY_SECRET, rzpOrder + "|pay_ok1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.payment.provider").value("razorpay"))
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"));
        client.send(GET, "/api/owner/restaurants/" + k.restaurantId + "/orders/" + id, k.owner, null).andExpect(status().isOk());

        // Confirming twice is harmless.
        confirm(id, rzpOrder, "pay_ok1", FakeRazorpay.sign(FakeRazorpay.KEY_SECRET, rzpOrder + "|pay_ok1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
        assertThat(jdbc.queryForObject("select provider_payment_id from payments where provider_order_id = ?", String.class, rzpOrder))
                .isEqualTo("pay_ok1");
    }

    @Test
    void authorisedPaymentIsCapturedForTheOrderAmount() throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();
        String rzpOrder = providerOrder(id);
        RAZORPAY.addPayment("pay_auth1", rzpOrder, TOTAL, "authorized");

        confirm(id, rzpOrder, "pay_auth1", FakeRazorpay.sign(FakeRazorpay.KEY_SECRET, rzpOrder + "|pay_auth1"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
        var captures = RAZORPAY.calls("POST", "/v1/payments/pay_auth1/capture");
        assertThat(captures).hasSize(1);
        assertThat(captures.getFirst().body().path("amount").asLong()).isEqualTo(TOTAL);
    }

    @Test
    void forgedOrMismatchedConfirmationsChangeNothing() throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();
        String rzpOrder = providerOrder(id);
        RAZORPAY.addPayment("pay_forged", rzpOrder, TOTAL, "captured");

        confirm(id, rzpOrder, "pay_forged", "0".repeat(64))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment signature is invalid"));
        // Valid signature, but the payment at Razorpay belongs to another order.
        RAZORPAY.addPayment("pay_elsewhere", "order_someone_else", TOTAL, "captured");
        confirm(id, rzpOrder, "pay_elsewhere", FakeRazorpay.sign(FakeRazorpay.KEY_SECRET, rzpOrder + "|pay_elsewhere"))
                .andExpect(status().isBadRequest());

        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.paymentStatus").value("PENDING"));
    }

    // ── webhooks ─────────────────────────────────────────────────────────────

    @Test
    void capturedWebhookMarksPaidAndIsIdempotent() throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();
        String rzpOrder = providerOrder(id);
        String eventId = "evt_" + UUID.randomUUID();
        String body = paymentEvent("payment.captured", "pay_wh1", rzpOrder, TOTAL);

        webhook(body, eventId).andExpect(status().isOk());
        webhook(body, eventId).andExpect(status().isOk()); // redelivery

        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        assertThat(jdbc.queryForObject("select count(*) from payment_webhook_events where event_id = ?", Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select raw_webhook_payload->>'event' from payments where provider_order_id = ?", String.class, rzpOrder))
                .isEqualTo("payment.captured");
    }

    @Test
    void webhookWithABadSignatureIsRejected() throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();
        String rzpOrder = providerOrder(id);
        String body = paymentEvent("payment.captured", "pay_bad", rzpOrder, TOTAL);
        String eventId = "evt_" + UUID.randomUUID();

        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Razorpay-Signature", FakeRazorpay.sign("not-the-secret", body))
                        .header("X-Razorpay-Event-Id", eventId))
                .andExpect(status().isBadRequest());
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());

        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        assertThat(jdbc.queryForObject("select count(*) from payment_webhook_events where event_id = ?", Integer.class, eventId)).isZero();
    }

    @Test
    void capturedAmountMustMatchTheOrder() throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();
        String rzpOrder = providerOrder(id);

        webhook(paymentEvent("payment.captured", "pay_cheap", rzpOrder, 100), "evt_" + UUID.randomUUID()).andExpect(status().isOk());

        client.send(GET, "/api/orders/" + id, k.customer, null)
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.payment.status").value("FAILED"));
    }

    @Test
    void failedWebhookFailsPrepaidOrdersButNotPayAtTable() throws Exception {
        String prepaid = k.placed(k.order("TAKEAWAY")).path("id").asText();
        String prepaidRzp = providerOrder(prepaid);
        webhook(paymentEvent("payment.failed", "pay_f1", prepaidRzp, TOTAL), "evt_" + UUID.randomUUID()).andExpect(status().isOk());
        client.send(GET, "/api/orders/" + prepaid, k.customer, null).andExpect(jsonPath("$.paymentStatus").value("FAILED"));

        Map<String, Object> dineIn = k.order("DINE_IN");
        dineIn.put("paymentMethod", "PAY_LATER");
        String payLater = k.placed(dineIn).path("id").asText();
        String payLaterRzp = providerOrder(payLater);
        webhook(paymentEvent("payment.failed", "pay_f2", payLaterRzp, TOTAL), "evt_" + UUID.randomUUID()).andExpect(status().isOk());
        client.send(GET, "/api/orders/" + payLater, k.customer, null)
                .andExpect(jsonPath("$.paymentStatus").value("PAY_LATER"))
                .andExpect(jsonPath("$.payment.status").value("FAILED"));
    }

    // ── refunds ──────────────────────────────────────────────────────────────

    @Test
    void rejectingAPaidOrderRefundsThroughRazorpayAndTheWebhookCompletesIt() throws Exception {
        String id = paidOrder("pay_r1");
        k.ownerSets(id, Map.of("status", "REJECTED", "reason", "Out of paneer"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUND_PENDING"));

        var refundCalls = RAZORPAY.calls("POST", "/v1/payments/pay_r1/refund");
        assertThat(refundCalls).hasSize(1);
        assertThat(refundCalls.getFirst().body().path("amount").asLong()).isEqualTo(TOTAL);
        assertThat(paymentStatus("pay_r1")).isEqualTo("REFUND_PENDING");

        webhook(refundEvent("rfnd_x1", "pay_r1"), "evt_" + UUID.randomUUID()).andExpect(status().isOk());
        client.send(GET, "/api/orders/" + id, k.customer, null)
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"));
    }

    @Test
    void refundsAreRetriedWithoutEverRefundingTwice() throws Exception {
        String id = paidOrder("pay_r2");
        RAZORPAY.refundsFail = true;
        k.ownerSets(id, Map.of("status", "REJECTED")).andExpect(jsonPath("$.paymentStatus").value("REFUND_PENDING"));
        assertThat(paymentStatus("pay_r2")).isEqualTo("REFUND_REQUESTED"); // gateway didn't accept it

        // Next attempt: Razorpay creates the refund but the response is lost (timeout).
        RAZORPAY.refundsFail = false;
        RAZORPAY.refundResponsesLost = true;
        paymentService.processRefund(paymentId("pay_r2"));
        assertThat(paymentStatus("pay_r2")).isEqualTo("REFUND_REQUESTED");

        // The retry finds the refund that already exists instead of creating another.
        RAZORPAY.refundResponsesLost = false;
        paymentService.processRefund(paymentId("pay_r2"));
        assertThat(paymentStatus("pay_r2")).isEqualTo("REFUND_PENDING");
        // Exactly two create calls: the failed one and the lost one. The reconciling retry made none.
        assertThat(RAZORPAY.calls("POST", "/v1/payments/pay_r2/refund")).hasSize(2);
        assertThat(jdbc.queryForObject("select provider_refund_id from payments where provider_payment_id = 'pay_r2'", String.class))
                .startsWith("rfnd_fake");
    }

    @Test
    void paymentLandingAfterCancellationIsRefundedAutomatically() throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();
        String rzpOrder = providerOrder(id);
        client.send(POST, "/api/orders/" + id + "/cancel", k.customer, null).andExpect(status().isOk());

        // The customer finished paying in the still-open checkout after cancelling.
        RAZORPAY.addPayment("pay_late", rzpOrder, TOTAL, "captured");
        webhook(paymentEvent("payment.captured", "pay_late", rzpOrder, TOTAL), "evt_" + UUID.randomUUID()).andExpect(status().isOk());

        assertThat(RAZORPAY.calls("POST", "/v1/payments/pay_late/refund")).hasSize(1);
        client.send(GET, "/api/orders/" + id, k.customer, null)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUND_PENDING"));
    }

    @Test
    void payAtTableCanBePaidOnlineAfterServing() throws Exception {
        Map<String, Object> dineIn = k.order("DINE_IN");
        dineIn.put("paymentMethod", "PAY_LATER");
        String id = k.placed(dineIn).path("id").asText();
        k.advance(id, "ACCEPTED", "PREPARING", "READY", "SERVED");

        String rzpOrder = providerOrder(id);
        RAZORPAY.addPayment("pay_table", rzpOrder, TOTAL, "captured");
        confirm(id, rzpOrder, "pay_table", FakeRazorpay.sign(FakeRazorpay.KEY_SECRET, rzpOrder + "|pay_table"))
                .andExpect(jsonPath("$.status").value("SERVED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String paidOrder(String paymentId) throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();
        String rzpOrder = providerOrder(id);
        RAZORPAY.addPayment(paymentId, rzpOrder, TOTAL, "captured");
        confirm(id, rzpOrder, paymentId, FakeRazorpay.sign(FakeRazorpay.KEY_SECRET, rzpOrder + "|" + paymentId))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
        return id;
    }

    private String providerOrder(String orderId) throws Exception {
        return client.body(k.startPayment(orderId).andExpect(status().isOk())).path("providerOrderId").asText();
    }

    private ResultActions confirm(String orderId, String rzpOrder, String paymentId, String signature) throws Exception {
        return client.send(POST, "/api/orders/" + orderId + "/payment/confirm", k.customer,
                new HashMap<>(Map.of("providerOrderId", rzpOrder, "providerPaymentId", paymentId, "signature", signature)));
    }

    private ResultActions webhook(String body, String eventId) throws Exception {
        return mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Razorpay-Signature", FakeRazorpay.sign(FakeRazorpay.WEBHOOK_SECRET, body))
                .header("X-Razorpay-Event-Id", eventId));
    }

    private static String paymentEvent(String event, String paymentId, String rzpOrder, long amount) {
        return """
                {"entity":"event","event":"%s","contains":["payment"],"payload":{"payment":{"entity":\
                {"id":"%s","entity":"payment","order_id":"%s","amount":%d,"currency":"INR","status":"%s",\
                "error_description":%s}}},"created_at":1760000000}"""
                .formatted(event, paymentId, rzpOrder, amount, event.equals("payment.failed") ? "failed" : "captured",
                        event.equals("payment.failed") ? "\"Card declined\"" : "null");
    }

    private static String refundEvent(String refundId, String paymentId) {
        return """
                {"entity":"event","event":"refund.processed","contains":["refund","payment"],"payload":{"refund":{"entity":\
                {"id":"%s","entity":"refund","payment_id":"%s","amount":%d,"status":"processed"}}},"created_at":1760000000}"""
                .formatted(refundId, paymentId, TOTAL);
    }

    private String paymentStatus(String providerPaymentId) {
        return jdbc.queryForObject("select status from payments where provider_payment_id = ?", String.class, providerPaymentId);
    }

    private UUID paymentId(String providerPaymentId) {
        return jdbc.queryForObject("select id from payments where provider_payment_id = ?", UUID.class, providerPaymentId);
    }
}
