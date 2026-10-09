package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import com.virtualkitchen.support.OwnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderLifecycleIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    AuthClient client;
    OrderTestKitchen k;
    String ordersPath;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        k = new OrderTestKitchen(client);
        ordersPath = "/api/owner/restaurants/" + k.restaurantId + "/orders";
    }

    // ── happy paths ──────────────────────────────────────────────────────────

    @Test
    void takeawayIsHiddenUntilPaidThenFlowsToPickedUp() throws Exception {
        JsonNode placed = k.placed(k.order("TAKEAWAY"));
        String id = placed.path("id").asText();
        assertThat(placed.path("status").asText()).isEqualTo("PLACED");
        assertThat(placed.path("paymentStatus").asText()).isEqualTo("PENDING");
        assertThat(placed.path("payment").path("providerOrderId").asText()).startsWith("mock_order_");
        assertThat(placed.path("displayNumber").asInt()).isEqualTo(1);
        // 2 × 24900 + 9900 = 59700; 5% GST = 2985
        assertThat(placed.path("subtotalPaise").asLong()).isEqualTo(59700);
        assertThat(placed.path("taxPaise").asLong()).isEqualTo(2985);
        assertThat(placed.path("totalPaise").asLong()).isEqualTo(62685);

        // Unpaid: the restaurant cannot see or act on it.
        client.send(GET, ordersPath, k.owner, null).andExpect(jsonPath("$[*].id", not(hasItem(id))));
        client.send(GET, ordersPath + "/" + id, k.owner, null).andExpect(status().isNotFound());
        k.ownerSets(id, "ACCEPTED").andExpect(status().isNotFound());
        client.send(GET, "/api/chef/orders", k.tandoor.chefToken(), null).andExpect(jsonPath("$[*].id", not(hasItem(id))));

        k.pay(id, "pay_123").andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        client.send(GET, ordersPath, k.owner, null).andExpect(jsonPath("$[*].id", hasItem(id)));

        k.ownerSets(id, "ACCEPTED").andExpect(status().isOk());
        k.chefPrepares(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.preparation.chefId").value(k.tandoor.chefId()))
                .andExpect(jsonPath("$.preparation.stationId").value(k.tandoor.stationId()))
                .andExpect(jsonPath("$.preparation.stationName").value("Tandoor"));
        k.chefSets(id, Map.of("status", "READY")).andExpect(status().isOk());
        k.ownerSets(id, "PICKED_UP").andExpect(status().isOk());

        client.send(GET, "/api/orders/" + id, k.customer, null)
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.timeline.paidAt", notNullValue()))
                .andExpect(jsonPath("$.timeline.acceptedAt", notNullValue()))
                .andExpect(jsonPath("$.timeline.preparingAt", notNullValue()))
                .andExpect(jsonPath("$.timeline.readyAt", notNullValue()))
                .andExpect(jsonPath("$.timeline.pickedUpAt", notNullValue()))
                .andExpect(jsonPath("$.preparation.chefDisplayName").value("Chef Ravi"));
        // Finished orders drop off the active list.
        client.send(GET, ordersPath, k.owner, null).andExpect(jsonPath("$[*].id", not(hasItem(id))));
    }

    @Test
    void dineInPayLaterIsVisibleImmediatelyAndEndsServed() throws Exception {
        Map<String, Object> body = k.order("DINE_IN");
        body.put("paymentMethod", "PAY_LATER");
        JsonNode placed = k.placed(body);
        String id = placed.path("id").asText();
        assertThat(placed.path("paymentStatus").asText()).isEqualTo("PAY_LATER");
        assertThat(placed.path("tableLabel").asText()).isEqualTo("T4");
        assertThat(placed.path("payment").isNull()).isTrue();

        client.send(GET, ordersPath, k.owner, null).andExpect(jsonPath("$[*].id", hasItem(id)));
        k.advance(id, "ACCEPTED", "PREPARING", "READY");
        k.ownerSets(id, "OUT_FOR_DELIVERY").andExpect(status().isConflict());
        k.ownerSets(id, "PICKED_UP").andExpect(status().isConflict());
        k.ownerSets(id, "SERVED").andExpect(status().isOk()).andExpect(jsonPath("$.timeline.servedAt", notNullValue()));
    }

    @Test
    void deliveryWithinRadiusFlowsToDelivered() throws Exception {
        JsonNode placed = k.placed(k.order("DELIVERY"));
        assertThat(placed.path("deliveryDistanceKm").asDouble()).isBetween(1.0, 2.0);
        String id = placed.path("id").asText();
        k.pay(id, "pay_ok").andExpect(status().isOk());

        k.advance(id, "ACCEPTED", "PREPARING", "READY");
        k.ownerSets(id, "SERVED").andExpect(status().isConflict());
        k.advance(id, "OUT_FOR_DELIVERY", "DELIVERED");
        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void displayNumbersIncrementPerRestaurant() throws Exception {
        assertThat(k.placed(k.order("TAKEAWAY")).path("displayNumber").asInt()).isEqualTo(1);
        assertThat(k.placed(k.order("TAKEAWAY")).path("displayNumber").asInt()).isEqualTo(2);
        OrderTestKitchen other = new OrderTestKitchen(client);
        assertThat(other.placed(other.order("TAKEAWAY")).path("displayNumber").asInt()).isEqualTo(1);
    }

    // ── illegal transitions over HTTP ────────────────────────────────────────

    @Test
    void skippingStatesIs409() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        for (String target : List.of("PREPARING", "READY", "SERVED", "PICKED_UP", "OUT_FOR_DELIVERY", "DELIVERED", "PLACED")) {
            k.ownerSets(id, Map.of("status", target, "chefId", k.tandoor.chefId(), "stationId", k.tandoor.stationId()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("Cannot move a TAKEAWAY order from PLACED to " + target)));
        }
        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.status").value("PLACED"));
    }

    @Test
    void wrongFinalStepForOrderTypeIs409() throws Exception {
        String takeaway = k.visibleOrder("TAKEAWAY");
        k.advance(takeaway, "ACCEPTED", "PREPARING", "READY");
        k.ownerSets(takeaway, "SERVED").andExpect(status().isConflict());
        k.ownerSets(takeaway, "OUT_FOR_DELIVERY").andExpect(status().isConflict());
        k.ownerSets(takeaway, "DELIVERED").andExpect(status().isConflict());
    }

    @Test
    void terminalOrdersCannotMove() throws Exception {
        String rejected = k.visibleOrder("TAKEAWAY");
        k.ownerSets(rejected, "REJECTED").andExpect(status().isOk());
        for (String target : List.of("ACCEPTED", "PLACED", "CANCELLED", "PREPARING", "REJECTED")) {
            k.ownerSets(rejected, target).andExpect(status().isConflict());
        }
        client.send(POST, "/api/orders/" + rejected + "/cancel", k.customer, null).andExpect(status().isConflict());

        String done = k.visibleOrder("TAKEAWAY");
        k.advance(done, "ACCEPTED", "PREPARING", "READY", "PICKED_UP");
        k.ownerSets(done, "READY").andExpect(status().isConflict());
        k.ownerSets(done, "CANCELLED").andExpect(status().isConflict());
    }

    @Test
    void cannotCancelOnceCookingStarted() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        k.advance(id, "ACCEPTED", "PREPARING");

        client.send(POST, "/api/orders/" + id + "/cancel", k.customer, Map.of("reason", "too slow"))
                .andExpect(status().isConflict());
        k.ownerSets(id, "CANCELLED").andExpect(status().isConflict());
        k.ownerSets(id, "REJECTED").andExpect(status().isConflict());
    }

    // ── who may do what ──────────────────────────────────────────────────────

    @Test
    void chefsMayOnlyPrepareAndMarkReady() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        k.chefSets(id, Map.of("status", "ACCEPTED")).andExpect(status().isForbidden());
        k.chefSets(id, Map.of("status", "REJECTED")).andExpect(status().isForbidden());
        k.chefSets(id, Map.of("status", "CANCELLED")).andExpect(status().isForbidden());
        k.ownerSets(id, "ACCEPTED").andExpect(status().isOk());
        k.chefPrepares(id).andExpect(status().isOk());
        k.chefSets(id, Map.of("status", "READY")).andExpect(status().isOk());
        k.chefSets(id, Map.of("status", "PICKED_UP")).andExpect(status().isForbidden());
    }

    @Test
    void chefCannotRecordSomeoneElseAsPreparer() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        k.ownerSets(id, "ACCEPTED").andExpect(status().isOk());
        OwnerFixtures.StaffedStation other = k.fixtures.consentedChefOnStation(k.owner, k.restaurantId, "Main line");

        k.chefSets(id, Map.of("status", "PREPARING", "stationId", k.tandoor.stationId(), "chefId", other.chefId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void customersCannotUseRestaurantEndpoints() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        client.send(PATCH, ordersPath + "/" + id + "/status", k.customer, Map.of("status", "ACCEPTED"))
                .andExpect(status().isForbidden());
        client.send(GET, "/api/chef/orders", k.customer, null).andExpect(status().isForbidden());
        client.send(POST, "/api/orders", k.owner, k.order("TAKEAWAY")).andExpect(status().isForbidden());
    }

    @Test
    void ordersArePrivate() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        String otherCustomer = client.registered("CUSTOMER").path("accessToken").asText();
        client.send(GET, "/api/orders/" + id, otherCustomer, null).andExpect(status().isNotFound());
        client.send(POST, "/api/orders/" + id + "/cancel", otherCustomer, null).andExpect(status().isNotFound());

        OrderTestKitchen other = new OrderTestKitchen(client);
        client.send(GET, "/api/owner/restaurants/" + other.restaurantId + "/orders/" + id, other.owner, null)
                .andExpect(status().isNotFound());
        client.send(PATCH, "/api/chef/orders/" + id + "/status", other.tandoor.chefToken(), Map.of("status", "READY"))
                .andExpect(status().isNotFound());
    }

    // ── PREPARING requirements ───────────────────────────────────────────────

    @Test
    void preparingNeedsAStationAndAnAssignedConsentedChef() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        k.ownerSets(id, "ACCEPTED").andExpect(status().isOk());

        k.chefSets(id, Map.of("status", "PREPARING")).andExpect(status().isBadRequest());
        k.ownerSets(id, Map.of("status", "PREPARING", "stationId", k.tandoor.stationId())).andExpect(status().isBadRequest());

        // A chef who isn't on that station.
        String grill = k.fixtures.createStation(k.owner, k.restaurantId, "Grill").path("id").asText();
        k.chefSets(id, Map.of("status", "PREPARING", "stationId", grill))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chef is not assigned to this station"));

        // The owner can record the assigned chef.
        k.ownerSets(id, Map.of("status", "PREPARING", "stationId", k.tandoor.stationId(), "chefId", k.tandoor.chefId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preparation.chefId").value(k.tandoor.chefId()));
    }

    @Test
    void chefWhoWithdrewConsentCannotStartPreparing() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        k.ownerSets(id, "ACCEPTED").andExpect(status().isOk());
        client.send(POST, "/api/chef/consent/withdraw", k.tandoor.chefToken(), null).andExpect(status().isOk());

        k.chefPrepares(id).andExpect(status().isConflict());
    }

    // ── placement validation ─────────────────────────────────────────────────

    @Test
    void placementValidation() throws Exception {
        Map<String, Object> payLaterTakeaway = k.order("TAKEAWAY");
        payLaterTakeaway.put("paymentMethod", "PAY_LATER");
        k.place(payLaterTakeaway).andExpect(status().isBadRequest());

        Map<String, Object> noTable = k.order("DINE_IN");
        noTable.remove("tableQrToken");
        k.place(noTable).andExpect(status().isBadRequest());

        Map<String, Object> badTable = k.order("DINE_IN");
        badTable.put("tableQrToken", "not-a-real-token");
        k.place(badTable).andExpect(status().isBadRequest());

        Map<String, Object> tableOnTakeaway = k.order("TAKEAWAY");
        tableOnTakeaway.put("tableQrToken", k.tableQr);
        k.place(tableOnTakeaway).andExpect(status().isBadRequest());

        Map<String, Object> noAddress = k.order("DELIVERY");
        noAddress.remove("deliveryAddress");
        k.place(noAddress).andExpect(status().isBadRequest());

        Map<String, Object> empty = k.order("TAKEAWAY");
        empty.put("items", List.of());
        k.place(empty).andExpect(status().isBadRequest());
    }

    @Test
    void deliveryOutsideRadiusIsRejected() throws Exception {
        Map<String, Object> far = k.order("DELIVERY");
        far.put("deliveryAddress", Map.of("address", "Whitefield", "latitude", 12.9698, "longitude", 77.7500));
        k.place(far)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("delivers within 5.0 km")));
    }

    @Test
    void unavailableOrForeignItemsAreRejected() throws Exception {
        client.send(PATCH, "/api/owner/restaurants/" + k.restaurantId + "/menu/items/" + k.dal + "/availability",
                k.owner, Map.of("available", false)).andExpect(status().isOk());
        k.place(k.order("TAKEAWAY"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Dal Makhani is currently unavailable"));

        OrderTestKitchen other = new OrderTestKitchen(client);
        Map<String, Object> foreign = k.order("TAKEAWAY");
        foreign.put("items", List.of(Map.of("menuItemId", other.tikka, "quantity", 1)));
        k.place(foreign).andExpect(status().isBadRequest());
    }

    @Test
    void closedUnsupportedOrUnapprovedRestaurantsRejectOrders() throws Exception {
        String owner = k.owner;
        Map<String, Object> closedBody = OwnerFixtures.restaurantBody("Closed Kitchen", OwnerFixtures.randomCity(),
                List.of(), List.of("TAKEAWAY"), List.of());
        String closed = k.fixtures.createRestaurant(owner, closedBody).path("id").asText();
        String category = k.fixtures.createCategory(owner, closed, "Mains").path("id").asText();
        String item = k.fixtures.createItem(owner, closed, OwnerFixtures.itemBody(category, "Thali", 19900, true)).path("id").asText();
        Map<String, Object> order = Map.of("restaurantId", closed, "orderType", "TAKEAWAY",
                "items", List.of(Map.of("menuItemId", item, "quantity", 1)));

        k.place(order).andExpect(status().isNotFound()); // not approved yet
        k.fixtures.approve(closed);
        k.place(order).andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("closed")));

        closedBody.put("openingHours", OwnerFixtures.ALWAYS_OPEN);
        client.send(PUT, "/api/owner/restaurants/" + closed, owner, closedBody).andExpect(status().isOk());
        k.place(Map.of("restaurantId", closed, "orderType", "DELIVERY", "items", List.of(Map.of("menuItemId", item, "quantity", 1)),
                        "deliveryAddress", Map.of("address", "x", "latitude", OrderTestKitchen.LAT, "longitude", OrderTestKitchen.LNG)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("does not offer DELIVERY")));
        k.place(order).andExpect(status().isCreated());
    }

    // ── payments, refunds, snapshots ─────────────────────────────────────────

    @Test
    void failedPaymentCanBeRetried() throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();

        k.pay(id, "fail_card_declined").andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("FAILED"));
        client.send(GET, ordersPath + "/" + id, k.owner, null).andExpect(status().isNotFound());
        k.pay(id, "pay_ok").andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        k.pay(id, "pay_again").andExpect(status().isConflict());
    }

    @Test
    void rejectingOrCancellingAPaidOrderRefundsIt() throws Exception {
        String rejected = k.visibleOrder("TAKEAWAY");
        k.ownerSets(rejected, Map.of("status", "REJECTED", "reason", "Out of chicken"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.statusReason").value("Out of chicken"))
                .andExpect(jsonPath("$.timeline.refundedAt", notNullValue()));

        String cancelled = k.visibleOrder("TAKEAWAY");
        k.ownerSets(cancelled, "ACCEPTED").andExpect(status().isOk());
        client.send(POST, "/api/orders/" + cancelled + "/cancel", k.customer, Map.of("reason", "Changed my mind"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
    }

    @Test
    void orderKeepsPriceAndNameSnapshot() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        JsonNode menu = client.body(client.send(GET, "/api/owner/restaurants/" + k.restaurantId + "/menu", k.owner, null));
        String category = menu.get(0).path("id").asText();
        client.send(PUT, "/api/owner/restaurants/" + k.restaurantId + "/menu/items/" + k.tikka, k.owner,
                OwnerFixtures.itemBody(category, "Chicken Tikka XL", 39900, false)).andExpect(status().isOk());

        client.send(GET, "/api/orders/" + id, k.customer, null)
                .andExpect(jsonPath("$.items[0].name").value("Chicken Tikka"))
                .andExpect(jsonPath("$.items[0].unitPricePaise").value(24900))
                .andExpect(jsonPath("$.items[0].lineTotalPaise").value(49800))
                .andExpect(jsonPath("$.totalPaise").value(62685));
    }

    @Test
    void customerListsOwnOrdersNewestFirst() throws Exception {
        String first = k.placed(k.order("TAKEAWAY")).path("id").asText();
        String second = k.placed(k.order("TAKEAWAY")).path("id").asText();

        client.send(GET, "/api/orders?size=10", k.customer, null)
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.items[1].id").value(first));
    }

    @Test
    void tableQrLookupIsPublic() throws Exception {
        client.send(GET, "/api/tables/" + k.tableQr, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableLabel").value("T4"))
                .andExpect(jsonPath("$.restaurantId").value(k.restaurantId));
        client.send(GET, "/api/tables/nope", null, null).andExpect(status().isNotFound());
    }
}
