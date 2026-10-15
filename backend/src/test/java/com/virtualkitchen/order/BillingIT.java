package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.billing.BillingService;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trials, payment set-up, charges and GST invoices, grace and suspension, plan changes and limits (mock gateway). */
class BillingIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired BillingService billing;

    AuthClient client;
    OrderTestKitchen k;
    String admin;
    String base;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        k = new OrderTestKitchen(client);
        admin = client.adminToken();
        base = "/api/owner/restaurants/" + k.restaurantId + "/billing";
        jdbc.update("delete from platform_settings where key like 'billing.%'");
    }

    @Test
    void newRestaurantsStartATrialThenPayByRecurringChargesWithGstInvoices() throws Exception {
        JsonNode overview = client.body(client.send(GET, base, k.owner, null).andExpect(status().isOk()));
        assertThat(overview.path("status").asText()).isEqualTo("TRIALING");
        assertThat(Instant.parse(overview.path("trialEndsAt").asText()))
                .isBetween(Instant.now().plus(Duration.ofDays(29)), Instant.now().plus(Duration.ofDays(31)));
        assertThat(overview.path("plan").path("code").asText()).isEqualTo("starter");
        JsonNode starter = overview.path("plan");
        assertThat(starter.path("pricePaise").asLong()).isEqualTo(99_900);
        assertThat(starter.path("totalPaise").asLong()).isEqualTo(117_882);   // + 18% GST
        assertThat(overview.path("plans").size()).isEqualTo(3);
        assertThat(overview.path("nextChargeAt").isNull()).isTrue();

        // Invoices need the legal name and GST state first.
        String starterId = starter.path("id").asText();
        client.send(POST, base + "/checkout", k.owner, Map.of("planId", starterId)).andExpect(status().isConflict());
        compliance("Test Kitchen Foods LLP", "29ABCDE1234F1Z5", null).andExpect(jsonPath("$.billingStateCode").value("29"));
        compliance("Test Kitchen Foods LLP", "29ABCDE1234F1Z5", "27").andExpect(status().isBadRequest());

        JsonNode checkout = client.body(client.send(POST, base + "/checkout", k.owner, Map.of("planId", starterId)).andExpect(status().isOk()));
        String sub = checkout.path("subscriptionId").asText();
        assertThat(checkout.path("amountPaise").asLong()).isEqualTo(117_882);
        client.send(POST, base + "/checkout/confirm", k.owner, Map.of("subscriptionId", "sub_other", "paymentId", "pay_1", "signature", "x"))
                .andExpect(status().isBadRequest());
        client.send(POST, base + "/checkout/confirm", k.owner, Map.of("subscriptionId", sub, "paymentId", "pay_auth", "signature", "mock"))
                .andExpect(jsonPath("$.paymentMethodReady").value(true))
                .andExpect(jsonPath("$.status").value("TRIALING"))
                .andExpect(jsonPath("$.nextChargeAt").value(overview.path("trialEndsAt").asText()));

        // Razorpay charges at the end of the trial (simulated): ACTIVE, invoiced with CGST + SGST (same state).
        simulate("CHARGED").andExpect(jsonPath("$.status").value("ACTIVE"));
        JsonNode paid = client.body(client.send(GET, base, k.owner, null));
        assertThat(paid.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(paid.path("nextChargeAt").asText()).isEqualTo(paid.path("currentPeriodEnd").asText());
        JsonNode invoice = paid.path("invoices").get(0);
        assertThat(invoice.path("number").asText()).matches("VLK/\\d{4}-\\d{2}/\\d{5}");
        assertThat(invoice.path("taxablePaise").asLong()).isEqualTo(99_900);
        assertThat(invoice.path("cgstPaise").asLong()).isEqualTo(8_991);
        assertThat(invoice.path("sgstPaise").asLong()).isEqualTo(8_991);
        assertThat(invoice.path("igstPaise").asLong()).isZero();

        MvcResult pdf = client.send(GET, base + "/invoices/" + invoice.path("id").asText() + ".pdf", k.owner, null)
                .andExpect(status().isOk()).andReturn();
        assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(pdf.getResponse().getHeader("Content-Disposition")).contains("attachment").contains(".pdf");
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");
        OrderTestKitchen other = new OrderTestKitchen(client);
        client.send(GET, base + "/invoices/" + invoice.path("id").asText() + ".pdf", other.owner, null).andExpect(status().isNotFound());
        client.send(GET, "/api/admin/billing/restaurants/" + k.restaurantId + "/invoices/" + invoice.path("id").asText() + ".pdf", admin, null)
                .andExpect(status().isOk());

        // Another state: IGST. Numbers keep counting up.
        String otherBase = "/api/owner/restaurants/" + other.restaurantId + "/billing";
        client.send(PUT, "/api/owner/restaurants/" + other.restaurantId + "/compliance", other.owner,
                Map.of("legalName", "Mumbai Eats Pvt Ltd", "billingStateCode", "27")).andExpect(status().isOk());
        String sub2 = client.body(client.send(POST, otherBase + "/checkout", other.owner, Map.of("planId", starterId))).path("subscriptionId").asText();
        client.send(POST, otherBase + "/checkout/confirm", other.owner, Map.of("subscriptionId", sub2, "paymentId", "pay_a", "signature", "m"));
        client.send(POST, "/api/admin/billing/restaurants/" + other.restaurantId + "/simulate", admin, Map.of("event", "CHARGED"));
        JsonNode igst = client.body(client.send(GET, otherBase, other.owner, null)).path("invoices").get(0);
        assertThat(igst.path("igstPaise").asLong()).isEqualTo(17_982);
        assertThat(igst.path("cgstPaise").asLong()).isZero();
        assertThat(number(igst)).isGreaterThan(number(invoice));
    }

    @Test
    void failedPaymentsGetGraceThenHideTheListingUntilPaid() throws Exception {
        approve();
        setUpPayment();
        simulate("CHARGED");
        String slug = client.body(client.send(GET, "/api/owner/restaurants/" + k.restaurantId, k.owner, null)).path("slug").asText();

        simulate("PENDING").andExpect(jsonPath("$.status").value("PAST_DUE"));
        JsonNode grace = client.body(client.send(GET, base, k.owner, null));
        assertThat(Instant.parse(grace.path("graceUntil").asText())).isBetween(Instant.now().plus(Duration.ofDays(6)), Instant.now().plus(Duration.ofDays(8)));
        simulate("HALTED"); // grace isn't extended by further failures
        assertThat(client.body(client.send(GET, base, k.owner, null)).path("graceUntil").asText()).isEqualTo(grace.path("graceUntil").asText());

        billing.enforce();
        mvc.perform(get("/api/restaurants/" + slug)).andExpect(status().isOk()); // still listed during grace

        jdbc.update("update restaurant_subscriptions set grace_until = now() - interval '1 minute' where restaurant_id = ?::uuid", k.restaurantId);
        billing.enforce();
        client.send(GET, base, k.owner, null).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/restaurants/" + slug)).andExpect(status().isNotFound());
        client.send(GET, "/api/owner/restaurants/" + k.restaurantId, k.owner, null)
                .andExpect(jsonPath("$.status").value("SUSPENDED")).andExpect(jsonPath("$.suspendedReason").value("BILLING"));
        client.send(GET, "/api/owner/restaurants/" + k.restaurantId + "/menu", k.owner, null).andExpect(status().isOk()); // data kept

        simulate("CHARGED").andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/restaurants/" + slug)).andExpect(status().isOk());

        // An admin's suspension isn't lifted by a payment.
        client.send(POST, "/api/admin/restaurants/" + k.restaurantId + "/suspend", admin, null).andExpect(jsonPath("$.suspendedReason").value("ADMIN"));
        simulate("CHARGED");
        client.send(GET, "/api/owner/restaurants/" + k.restaurantId, k.owner, null).andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void anUnpaidTrialAndAFinishedCancellationBothEndInSuspension() throws Exception {
        approve();
        jdbc.update("update restaurant_subscriptions set trial_ends_at = now() - interval '1 hour' where restaurant_id = ?::uuid", k.restaurantId);
        billing.enforce();
        JsonNode overdue = client.body(client.send(GET, base, k.owner, null));
        assertThat(overdue.path("status").asText()).isEqualTo("PAST_DUE");
        jdbc.update("update restaurant_subscriptions set grace_until = now() - interval '1 minute' where restaurant_id = ?::uuid", k.restaurantId);
        billing.enforce();
        client.send(GET, base, k.owner, null).andExpect(jsonPath("$.status").value("SUSPENDED"));

        // Paying brings it back; cancelling runs to the end of the paid month.
        setUpPayment();
        simulate("CHARGED").andExpect(jsonPath("$.status").value("ACTIVE"));
        client.send(POST, base + "/cancel", k.owner, null).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancelRequested").value(true));
        billing.enforce();
        client.send(GET, "/api/owner/restaurants/" + k.restaurantId, k.owner, null).andExpect(jsonPath("$.status").value("ACTIVE"));
        jdbc.update("update restaurant_subscriptions set current_period_end = now() - interval '1 minute' where restaurant_id = ?::uuid", k.restaurantId);
        billing.enforce();
        client.send(GET, "/api/owner/restaurants/" + k.restaurantId, k.owner, null).andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void upgradesApplyNowDowngradesAtTheNextChargeAndPlansLimitStations() throws Exception {
        JsonNode plans = client.body(client.send(GET, base, k.owner, null)).path("plans");
        String starter = plans.get(0).path("id").asText();
        String growth = plans.get(1).path("id").asText();

        // Starter allows 2 stations (OrderTestKitchen made one).
        k.fixtures.createStation(k.owner, k.restaurantId, "Grill");
        client.send(POST, "/api/owner/restaurants/" + k.restaurantId + "/stations", k.owner, Map.of("name", "Tawa"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("allows 2 stations")));

        setUpPayment();
        simulate("CHARGED");
        client.send(PUT, base + "/plan", k.owner, Map.of("planId", growth)).andExpect(jsonPath("$.plan.code").value("growth"));
        client.send(POST, "/api/owner/restaurants/" + k.restaurantId + "/stations", k.owner, Map.of("name", "Tawa")).andExpect(status().isCreated());

        // Three stations don't fit Starter: refused until one is removed.
        client.send(PUT, base + "/plan", k.owner, Map.of("planId", starter)).andExpect(status().isConflict());
        String tawa = jdbc.queryForObject("select id::text from kitchen_stations where restaurant_id = ?::uuid and name = 'Tawa'", String.class, k.restaurantId);
        client.send(org.springframework.http.HttpMethod.DELETE, "/api/owner/restaurants/" + k.restaurantId + "/stations/" + tawa, k.owner, null);
        client.send(PUT, base + "/plan", k.owner, Map.of("planId", starter))
                .andExpect(jsonPath("$.plan.code").value("growth")).andExpect(jsonPath("$.pendingPlan.code").value("starter"));
        simulate("CHARGED");
        client.send(GET, base, k.owner, null).andExpect(jsonPath("$.plan.code").value("starter")).andExpect(jsonPath("$.pendingPlan").doesNotExist());
    }

    @Test
    void adminsManagePlansAndTrialLengthAndEveryActionIsAudited() throws Exception {
        String owner = client.approvedOwnerToken();
        client.send(GET, "/api/admin/billing/plans", owner, null).andExpect(status().isForbidden());
        JsonNode created = client.body(client.send(POST, "/api/admin/billing/plans", admin, Map.of("code", "it-plan-" + System.nanoTime() % 100000,
                "name", "IT Plan", "pricePaise", 150_000, "features", Map.of("maxStations", 3, "clipRetentionDays", 45, "analytics", true),
                "active", false, "sortOrder", 9)).andExpect(status().isCreated()));
        String planId = created.path("id").asText();
        assertThat(created.path("totalPaise").asLong()).isEqualTo(177_000);
        client.send(PUT, "/api/admin/billing/plans/" + planId, admin, Map.of("name", "IT Plan", "pricePaise", 175_000,
                "features", Map.of("maxStations", 3, "clipRetentionDays", 45, "analytics", true), "active", false, "sortOrder", 9))
                .andExpect(jsonPath("$.pricePaise").value(175_000));

        client.send(PUT, "/api/admin/billing/settings", admin, Map.of("trialDays", 14, "graceDays", 3))
                .andExpect(jsonPath("$.trialDays").value(14)).andExpect(jsonPath("$.simulationAvailable").value(true));
        OrderTestKitchen later = new OrderTestKitchen(client);
        JsonNode trial = client.body(client.send(GET, "/api/owner/restaurants/" + later.restaurantId + "/billing", later.owner, null));
        assertThat(Instant.parse(trial.path("trialEndsAt").asText())).isBefore(Instant.now().plus(Duration.ofDays(15)));

        client.send(POST, "/api/admin/restaurants/" + k.restaurantId + "/suspend", admin, null);
        JsonNode log = client.body(client.send(GET, "/api/admin/audit?size=200", admin, null).andExpect(status().isOk()));
        String all = log.toString();
        assertThat(all).contains("PLAN_CREATED", "PLAN_UPDATED", "BILLING_SETTINGS_UPDATED", "RESTAURANT_SUSPENDED", "Test Admin");
        JsonNode update = client.body(client.send(GET, "/api/admin/audit?action=PLAN_UPDATED", admin, null)).path("items").get(0);
        assertThat(update.path("details").path("before").path("pricePaise").asLong()).isEqualTo(150_000);
        assertThat(update.path("details").path("after").path("pricePaise").asLong()).isEqualTo(175_000);
        client.send(GET, "/api/admin/audit", owner, null).andExpect(status().isForbidden());

        // Restaurant detail: owner, licence details, billing (viewing it is audited too).
        compliance("Test Kitchen Foods LLP", null, "29");
        client.send(PUT, "/api/owner/restaurants/" + k.restaurantId + "/compliance", k.owner,
                Map.of("fssaiNumber", "12345678901234", "legalName", "Test Kitchen Foods LLP", "billingStateCode", "29")).andExpect(status().isOk());
        client.send(GET, "/api/admin/restaurants/" + k.restaurantId, admin, null)
                .andExpect(jsonPath("$.owner.phone").isNotEmpty())
                .andExpect(jsonPath("$.compliance.fssaiNumber").value("12345678901234"))
                .andExpect(jsonPath("$.subscription.status").value("TRIALING"));
        client.send(GET, "/api/admin/audit?action=RESTAURANT_VIEWED", admin, null).andExpect(jsonPath("$.items[0].targetId").value(k.restaurantId));
    }

    @Test
    void metricsCountTheDayAndMrr() throws Exception {
        String order = k.visibleOrder("TAKEAWAY");
        setUpPayment();
        simulate("CHARGED");
        JsonNode m = client.body(client.send(GET, "/api/admin/metrics?days=7", admin, null).andExpect(status().isOk()));
        assertThat(m.path("series").size()).isEqualTo(7);
        assertThat(m.path("totals").path("orders").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(m.path("totals").path("gmvPaise").asLong()).isGreaterThan(0);
        assertThat(m.path("mrrPaise").asLong()).isGreaterThanOrEqualTo(99_900);
        assertThat(m.path("subscriptionsByStatus").path("ACTIVE").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(order).isNotBlank();
        client.send(GET, "/api/admin/metrics?days=999", admin, null).andExpect(status().isBadRequest());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions compliance(String legalName, String gstin, String state) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("legalName", legalName);
        if (gstin != null) body.put("gstin", gstin);
        if (state != null) body.put("billingStateCode", state);
        return client.send(PUT, "/api/owner/restaurants/" + k.restaurantId + "/compliance", k.owner, body);
    }

    private void setUpPayment() throws Exception {
        compliance("Test Kitchen Foods LLP", null, "29").andExpect(status().isOk());
        String plan = client.body(client.send(GET, base, k.owner, null)).path("plan").path("id").asText();
        String sub = client.body(client.send(POST, base + "/checkout", k.owner, Map.of("planId", plan)).andExpect(status().isOk()))
                .path("subscriptionId").asText();
        client.send(POST, base + "/checkout/confirm", k.owner, Map.of("subscriptionId", sub, "paymentId", "pay_auth", "signature", "m"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions simulate(String event) throws Exception {
        return client.send(POST, "/api/admin/billing/restaurants/" + k.restaurantId + "/simulate", admin, Map.of("event", event))
                .andExpect(status().isOk());
    }

    private void approve() throws Exception {
        client.send(GET, "/api/owner/restaurants/" + k.restaurantId, k.owner, null).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private static int number(JsonNode invoice) {
        String n = invoice.path("number").asText();
        return Integer.parseInt(n.substring(n.lastIndexOf('/') + 1));
    }
}
