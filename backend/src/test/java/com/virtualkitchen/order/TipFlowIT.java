package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import com.virtualkitchen.support.OwnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Payout onboarding, tipping, follows and tip visibility with the mock gateway (Razorpay itself: RazorpayTipIT). */
class TipFlowIT extends IntegrationTest {

    static final Map<String, Object> PAYOUT = Map.of("legalName", "Ravi Kumar", "email", "ravi@example.test",
            "bankAccountNumber", "123456789012", "ifsc", "HDFC0001234", "pan", "ABCPK1234F",
            "street", "1 MG Road", "city", "Bengaluru", "state", "Karnataka", "postalCode", "560001");

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
    void chefSetsUpPayoutsWithoutTheBankDetailsBeingStored() throws Exception {
        String chef = k.tandoor.chefToken();
        client.send(GET, "/api/chef/payout-account", chef, null).andExpect(jsonPath("$.status").value("NOT_SET_UP"));

        Map<String, Object> badPan = new HashMap<>(PAYOUT);
        badPan.put("pan", "ABCDE1234F"); // a company PAN, not an individual's
        client.send(PUT, "/api/chef/payout-account", chef, badPan).andExpect(status().isBadRequest());

        JsonNode pending = client.body(client.send(PUT, "/api/chef/payout-account", chef, PAYOUT).andExpect(status().isOk()));
        assertThat(pending.path("status").asText()).isEqualTo("PENDING_KYC");
        assertThat(pending.path("bankAccountMasked").asText()).isEqualTo("HDFC ••••9012");
        assertThat(pending.path("panMasked").asText()).isEqualTo("••••••234F");
        assertThat(pending.toString()).doesNotContain("123456789012").doesNotContain("ABCPK1234F").doesNotContain("HDFC0001234");

        Map<String, Object> row = jdbc.queryForMap("select * from chef_payout_accounts where chef_id = ?::uuid", k.tandoor.chefId());
        assertThat(row.toString()).doesNotContain("123456789012").doesNotContain("ABCPK1234F").doesNotContain("HDFC0001234");

        // Not tippable until the gateway activates the account.
        String order = readyOrder();
        client.send(GET, "/api/orders/" + order + "/tips", k.customer, null).andExpect(jsonPath("$.chefs.length()").value(0));
        client.send(POST, "/api/orders/" + order + "/tips", k.customer, tip(k.tandoor.chefId(), 5000, "CLIP")).andExpect(status().isConflict());

        client.send(POST, "/api/chef/payout-account/refresh", chef, null).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activatedAt").isNotEmpty());

        // New bank account OK; changing the PAN isn't.
        Map<String, Object> newBank = new HashMap<>(PAYOUT);
        newBank.put("bankAccountNumber", "998877665544");
        newBank.put("ifsc", "ICIC0000001");
        client.send(PUT, "/api/chef/payout-account", chef, newBank).andExpect(jsonPath("$.bankAccountMasked").value("ICIC ••••5544"));
        Map<String, Object> newPan = new HashMap<>(PAYOUT);
        newPan.put("pan", "ABCPK9999Z");
        client.send(PUT, "/api/chef/payout-account", chef, newPan).andExpect(status().isConflict());

        client.send(GET, "/api/chef/payout-account", k.owner, null).andExpect(status().isForbidden());
        client.send(GET, "/api/chef/payout-account", k.customer, null).andExpect(status().isForbidden());
    }

    @Test
    void customerTipsTheChefWhoCookedTheirOrderAfterTheClip() throws Exception {
        activatePayouts(k.tandoor.chefToken());
        String order = readyOrder();

        JsonNode options = client.body(client.send(GET, "/api/orders/" + order + "/tips", k.customer, null).andExpect(status().isOk()));
        assertThat(options.path("chefs").get(0).path("chefId").asText()).isEqualTo(k.tandoor.chefId());
        assertThat(options.path("chefs").get(0).path("cookedThisOrder").asBoolean()).isTrue();
        assertThat(options.path("presetsPaise").toString()).isEqualTo("[2000,5000,10000]");

        client.send(POST, "/api/orders/" + order + "/tips", k.customer, tip(k.tandoor.chefId(), 500, "CLIP")).andExpect(status().isBadRequest());
        client.send(POST, "/api/orders/" + order + "/tips", k.customer, tip(k.tandoor.chefId(), 600_000, "CLIP")).andExpect(status().isBadRequest());

        JsonNode started = client.body(client.send(POST, "/api/orders/" + order + "/tips", k.customer, tip(k.tandoor.chefId(), 5000, "CLIP"))
                .andExpect(status().isOk()));
        String tipId = started.path("tip").path("id").asText();
        JsonNode checkout = started.path("checkout");
        assertThat(checkout.path("amountPaise").asLong()).isEqualTo(5000);
        assertThat(checkout.path("description").asText()).startsWith("Tip for ");
        assertThat(jdbc.queryForObject("select chef_amount_paise from tips where id = ?::uuid", Long.class, tipId)).isEqualTo(5000L);

        JsonNode paid = client.body(confirm(order, tipId, checkout.path("providerOrderId").asText(), "pay_mock_1").andExpect(status().isOk()));
        assertThat(paid.path("status").asText()).isEqualTo("TRANSFERRED"); // the mock settles transfers at once
        confirm(order, tipId, checkout.path("providerOrderId").asText(), "pay_mock_1").andExpect(jsonPath("$.status").value("TRANSFERRED"));
        client.send(GET, "/api/orders/" + order + "/tips", k.customer, null).andExpect(jsonPath("$.tips[0].amountPaise").value(5000));

        // A declined payment.
        JsonNode second = client.body(client.send(POST, "/api/orders/" + order + "/tips", k.customer, tip(k.tandoor.chefId(), 2000, "CLIP")));
        confirm(order, second.path("tip").path("id").asText(), second.path("checkout").path("providerOrderId").asText(), "fail_mock")
                .andExpect(jsonPath("$.status").value("FAILED"));

        // The chef sees it (no customer details), the public profile counts it, the owner sees the count only.
        JsonNode mine = client.body(client.send(GET, "/api/chef/tips", k.tandoor.chefToken(), null).andExpect(status().isOk()));
        assertThat(mine.path("tipCount").asLong()).isEqualTo(1);
        assertThat(mine.path("totalChefAmountPaise").asLong()).isEqualTo(5000);
        assertThat(mine.path("items").get(0).has("customerId")).isFalse();
        mvc.perform(get("/api/public/chefs/" + k.tandoor.chefId())).andExpect(jsonPath("$.tipCount").value(1))
                .andExpect(jsonPath("$.tippable").value(true)).andExpect(jsonPath("$.hygieneScore").doesNotExist());
        JsonNode chefs = client.body(client.send(GET, "/api/owner/restaurants/" + k.restaurantId + "/chefs", k.owner, null));
        assertThat(chefs.get(0).path("tipCount").asLong()).isEqualTo(1);
        assertThat(chefs.get(0).path("payoutStatus").asText()).isEqualTo("ACTIVE");
        assertThat(chefs.toString()).doesNotContain("totalChefAmountPaise");

        // Only the order's customer.
        String stranger = client.registered("CUSTOMER").path("accessToken").asText();
        client.send(POST, "/api/orders/" + order + "/tips", stranger, tip(k.tandoor.chefId(), 5000, "CLIP")).andExpect(status().isNotFound());
        client.send(POST, "/api/orders/" + order + "/tips", k.tandoor.chefToken(), tip(k.tandoor.chefId(), 5000, "CLIP")).andExpect(status().isForbidden());
    }

    @Test
    void followedChefsCanBeTippedAtCheckoutOthersCannot() throws Exception {
        OwnerFixtures.StaffedStation grill = k.fixtures.consentedChefOnStation(k.owner, k.restaurantId, "Grill");
        activatePayouts(grill.chefToken());
        String order = k.visibleOrder("TAKEAWAY");

        client.send(POST, "/api/orders/" + order + "/tips", k.customer, tip(grill.chefId(), 5000, "CHECKOUT")).andExpect(status().isForbidden());
        client.send(PUT, "/api/chefs/" + grill.chefId() + "/follow", k.customer, null).andExpect(status().isNoContent());
        client.send(GET, "/api/chefs/" + grill.chefId() + "/follow", k.customer, null).andExpect(jsonPath("$.following").value(true));
        client.send(GET, "/api/me/followed-chefs?restaurantId=" + k.restaurantId, k.customer, null)
                .andExpect(jsonPath("$[0].chefId").value(grill.chefId()))
                .andExpect(jsonPath("$[0].tippable").value(true));
        mvc.perform(get("/api/public/chefs/" + grill.chefId())).andExpect(jsonPath("$.followerCount").value(1));

        JsonNode options = client.body(client.send(GET, "/api/orders/" + order + "/tips", k.customer, null));
        assertThat(options.path("chefs").get(0).path("cookedThisOrder").asBoolean()).isFalse();
        JsonNode started = client.body(client.send(POST, "/api/orders/" + order + "/tips", k.customer, tip(grill.chefId(), 10000, "CHECKOUT"))
                .andExpect(status().isOk()));
        confirm(order, started.path("tip").path("id").asText(), started.path("checkout").path("providerOrderId").asText(), "pay_mock_2")
                .andExpect(jsonPath("$.status").value("TRANSFERRED")).andExpect(jsonPath("$.source").value("CHECKOUT"));

        client.send(DELETE, "/api/chefs/" + grill.chefId() + "/follow", k.customer, null).andExpect(status().isNoContent());
        client.send(GET, "/api/me/followed-chefs", k.customer, null).andExpect(jsonPath("$.length()").value(0));
        client.send(PUT, "/api/chefs/" + java.util.UUID.randomUUID() + "/follow", k.customer, null).andExpect(status().isNotFound());
        client.send(PUT, "/api/chefs/" + grill.chefId() + "/follow", k.owner, null).andExpect(status().isForbidden());
    }

    @Test
    void guestsTipWithTheirOrderToken() throws Exception {
        activatePayouts(k.tandoor.chefToken());
        Map<String, Object> body = new HashMap<>(Map.of("name", "Guest", "phone", AuthClient.randomPhone(),
                "tableSessionToken", k.tableSession, "paymentMethod", "PAY_LATER",
                "items", List.of(Map.of("menuItemId", k.dal, "quantity", 1))));
        JsonNode placed = client.body(client.postJson("/api/guest/orders", body).andExpect(status().isCreated()));
        String order = placed.path("order").path("id").asText();
        String token = placed.path("guestToken").asText();
        k.advance(order, "ACCEPTED", "PREPARING");
        k.chefSets(order, Map.of("status", "READY")).andExpect(status().isOk());

        String base = "/api/guest/orders/" + order + "/tips";
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        JsonNode started = client.body(mvc.perform(post(base).header("X-Guest-Order-Token", token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(tip(k.tandoor.chefId(), 2000, "CLIP"))))
                .andExpect(status().isOk()));
        mvc.perform(post(base + "/" + started.path("tip").path("id").asText() + "/confirm").header("X-Guest-Order-Token", token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("providerOrderId", started.path("checkout").path("providerOrderId").asText(),
                                "providerPaymentId", "pay_guest", "signature", "mock"))))
                .andExpect(jsonPath("$.status").value("TRANSFERRED"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void activatePayouts(String chefToken) throws Exception {
        client.send(PUT, "/api/chef/payout-account", chefToken, PAYOUT).andExpect(status().isOk());
        client.send(POST, "/api/chef/payout-account/refresh", chefToken, null).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private String readyOrder() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        k.advance(id, "ACCEPTED", "PREPARING");
        k.chefSets(id, Map.of("status", "READY")).andExpect(status().isOk());
        return id;
    }

    private static Map<String, Object> tip(String chefId, long paise, String source) {
        return Map.of("chefId", chefId, "amountPaise", paise, "source", source);
    }

    private ResultActions confirm(String orderId, String tipId, String providerOrderId, String paymentId) throws Exception {
        return client.send(POST, "/api/orders/" + orderId + "/tips/" + tipId + "/confirm", k.customer,
                Map.of("providerOrderId", providerOrderId, "providerPaymentId", paymentId, "signature", "mock"));
    }
}
