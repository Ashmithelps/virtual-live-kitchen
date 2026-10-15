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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Route linked accounts and tip transfers through the real Razorpay providers against an in-process fake API. */
@TestPropertySource(properties = {
        "app.payment.provider=razorpay",
        "app.payment.razorpay.key-id=" + FakeRazorpay.KEY_ID,
        "app.payment.razorpay.key-secret=" + FakeRazorpay.KEY_SECRET,
        "app.payment.razorpay.webhook-secret=" + FakeRazorpay.WEBHOOK_SECRET,
})
class RazorpayTipIT extends IntegrationTest {

    static final FakeRazorpay RAZORPAY = new FakeRazorpay();
    static final String WEBHOOK = "/api/payments/razorpay/webhook";

    @DynamicPropertySource
    static void razorpayUrl(DynamicPropertyRegistry registry) {
        registry.add("app.payment.razorpay.api-base-url", RAZORPAY::baseUrl);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    AuthClient client;
    OrderTestKitchen k;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        k = new OrderTestKitchen(client);
    }

    @Test
    void linkedAccountActivatesByWebhookAndTipsTransferToIt() throws Exception {
        String chef = k.tandoor.chefToken();
        int before = RAZORPAY.calls.size();
        client.send(PUT, "/api/chef/payout-account", chef, TipFlowIT.PAYOUT).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_KYC"))
                .andExpect(jsonPath("$.providerStatus").value("under_review"));

        // Account → stakeholder → Route product → settlement details; the bank details go only to Razorpay.
        var setup = RAZORPAY.calls.subList(before, RAZORPAY.calls.size());
        assertThat(setup).extracting(FakeRazorpay.Call::method).containsExactly("POST", "POST", "POST", "PATCH");
        JsonNode account = setup.get(0).body();
        assertThat(account.path("type").asText()).isEqualTo("route");
        assertThat(account.path("business_type").asText()).isEqualTo("individual");
        assertThat(account.path("legal_info").path("pan").asText()).isEqualTo("ABCPK1234F");
        assertThat(account.path("phone").asText()).doesNotStartWith("+");
        JsonNode settlements = setup.get(3).body().path("settlements");
        assertThat(settlements.path("account_number").asText()).isEqualTo("123456789012");
        assertThat(settlements.path("ifsc_code").asText()).isEqualTo("HDFC0001234");
        String accountId = jdbc.queryForObject("select provider_account_id from chef_payout_accounts where chef_id = ?::uuid", String.class, k.tandoor.chefId());
        String productId = jdbc.queryForObject("select provider_product_id from chef_payout_accounts where chef_id = ?::uuid", String.class, k.tandoor.chefId());

        // Razorpay activates it (Dashboard / review) and tells us by webhook.
        RAZORPAY.activate(productId);
        webhook("""
                {"entity":"event","account_id":"%s","event":"product.route.activated",
                 "payload":{"merchant_product":{"entity":{"id":"%s","activation_status":"activated"}}}}
                """.formatted(accountId, productId)).andExpect(status().isOk());
        client.send(GET, "/api/chef/payout-account", chef, null).andExpect(jsonPath("$.status").value("ACTIVE"));

        // Tip: an order with a Route transfer of the whole amount to the chef's account.
        String order = payLaterOrder();
        k.advance(order, "ACCEPTED", "PREPARING");
        k.chefSets(order, Map.of("status", "READY")).andExpect(status().isOk());
        JsonNode started = client.body(client.send(POST, "/api/orders/" + order + "/tips", k.customer,
                Map.of("chefId", k.tandoor.chefId(), "amountPaise", 5000, "source", "CLIP")).andExpect(status().isOk()));
        String tipId = started.path("tip").path("id").asText();
        String rzpOrder = started.path("checkout").path("providerOrderId").asText();
        assertThat(started.path("checkout").path("keyId").asText()).isEqualTo(FakeRazorpay.KEY_ID);
        JsonNode created = RAZORPAY.calls("POST", "/v1/orders").getLast().body();
        assertThat(created.path("amount").asLong()).isEqualTo(5000);
        JsonNode transfer = created.path("transfers").get(0);
        assertThat(transfer.path("account").asText()).isEqualTo(accountId);
        assertThat(transfer.path("amount").asLong()).isEqualTo(5000);
        assertThat(transfer.path("on_hold").asBoolean()).isFalse();

        // Checkout comes back signed; Razorpay holds the captured payment.
        String paymentId = "pay_tip_" + UUID.randomUUID().toString().substring(0, 8);
        RAZORPAY.addPayment(paymentId, rzpOrder, 5000, "captured");
        client.send(POST, "/api/orders/" + order + "/tips/" + tipId + "/confirm", k.customer,
                        Map.of("providerOrderId", rzpOrder, "providerPaymentId", paymentId, "signature", "forged"))
                .andExpect(status().isBadRequest());
        client.send(POST, "/api/orders/" + order + "/tips/" + tipId + "/confirm", k.customer, Map.of("providerOrderId", rzpOrder,
                        "providerPaymentId", paymentId, "signature", FakeRazorpay.sign(FakeRazorpay.KEY_SECRET, rzpOrder + "|" + paymentId)))
                .andExpect(jsonPath("$.status").value("PAID"));

        // Webhooks: the capture again (idempotent, not a food order), then Route's transfer.
        webhook("""
                {"entity":"event","event":"payment.captured","payload":{"payment":{"entity":{"id":"%s","order_id":"%s","amount":5000}}}}
                """.formatted(paymentId, rzpOrder)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from tips where id = ?::uuid", String.class, tipId)).isEqualTo("PAID");
        webhook("""
                {"entity":"event","event":"transfer.processed","payload":{"transfer":{"entity":{"id":"trf_1","source":"%s","recipient":"%s","amount":5000}}}}
                """.formatted(paymentId, accountId)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from tips where id = ?::uuid", String.class, tipId)).isEqualTo("TRANSFERRED");
        assertThat(jdbc.queryForObject("select provider_transfer_id from tips where id = ?::uuid", String.class, tipId)).isEqualTo("trf_1");
        client.send(GET, "/api/chef/tips", chef, null).andExpect(jsonPath("$.items[0].status").value("TRANSFERRED"));
    }

    @Test
    void failedTipPaymentsAndUnknownAccountsAreHandled() throws Exception {
        // Webhook for someone else's linked account: acknowledged, nothing changes.
        webhook("""
                {"entity":"event","account_id":"acc_not_ours","event":"product.route.activated",
                 "payload":{"merchant_product":{"entity":{"id":"acc_prd_not_ours","activation_status":"activated"}}}}
                """).andExpect(status().isOk());

        String chef = k.tandoor.chefToken();
        client.send(PUT, "/api/chef/payout-account", chef, TipFlowIT.PAYOUT).andExpect(status().isOk());
        String productId = jdbc.queryForObject("select provider_product_id from chef_payout_accounts where chef_id = ?::uuid", String.class, k.tandoor.chefId());
        RAZORPAY.activate(productId);
        client.send(POST, "/api/chef/payout-account/refresh", chef, null).andExpect(jsonPath("$.status").value("ACTIVE"));

        String order = payLaterOrder();
        k.advance(order, "ACCEPTED", "PREPARING");
        k.chefSets(order, Map.of("status", "READY")).andExpect(status().isOk());
        JsonNode started = client.body(client.send(POST, "/api/orders/" + order + "/tips", k.customer,
                Map.of("chefId", k.tandoor.chefId(), "amountPaise", 2000, "source", "CLIP")));
        String rzpOrder = started.path("checkout").path("providerOrderId").asText();
        webhook("""
                {"entity":"event","event":"payment.failed","payload":{"payment":{"entity":{"id":"pay_f1","order_id":"%s","amount":2000,
                 "error_description":"Card declined"}}}}
                """.formatted(rzpOrder)).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("select status, failure_reason from tips where id = ?::uuid", started.path("tip").path("id").asText()))
                .containsEntry("status", "FAILED").containsEntry("failure_reason", "Card declined");
    }

    /** Visible to the kitchen at once (no payment needed first). */
    private String payLaterOrder() throws Exception {
        var body = k.order("DINE_IN");
        body.put("paymentMethod", "PAY_LATER");
        return k.placed(body).path("id").asText();
    }

    private ResultActions webhook(String body) throws Exception {
        return mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Razorpay-Signature", FakeRazorpay.sign(FakeRazorpay.WEBHOOK_SECRET, body))
                .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID()));
    }
}
