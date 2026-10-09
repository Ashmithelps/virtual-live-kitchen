package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.OwnerFixtures;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.virtualkitchen.support.OwnerFixtures.ALWAYS_OPEN;
import static com.virtualkitchen.support.OwnerFixtures.itemBody;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An ACTIVE, always-open restaurant at MG Road, Bengaluru (delivers within 5 km) with a two-item
 * menu, one table, and a consented chef on the "Tandoor" station; plus a customer.
 */
class OrderTestKitchen {

    static final double LAT = 12.9716;
    static final double LNG = 77.5946;

    final AuthClient client;
    final OwnerFixtures fixtures;
    final String owner;
    final String restaurantId;
    final String tikka;      // 24900, non-veg
    final String dal;        // 9900, veg
    final String tableQr;
    final OwnerFixtures.StaffedStation tandoor;
    final String customer;

    OrderTestKitchen(AuthClient client) throws Exception {
        this.client = client;
        this.fixtures = new OwnerFixtures(client);
        owner = client.approvedOwnerToken();
        Map<String, Object> body = OwnerFixtures.restaurantBody("Test Kitchen", OwnerFixtures.randomCity(), List.of(),
                List.of("DINE_IN", "TAKEAWAY", "DELIVERY"), ALWAYS_OPEN);
        restaurantId = fixtures.createRestaurant(owner, body).path("id").asText();
        fixtures.approve(restaurantId);
        String category = fixtures.createCategory(owner, restaurantId, "Mains").path("id").asText();
        tikka = fixtures.createItem(owner, restaurantId, itemBody(category, "Chicken Tikka", 24900, false)).path("id").asText();
        dal = fixtures.createItem(owner, restaurantId, itemBody(category, "Dal Makhani", 9900, true)).path("id").asText();
        tableQr = client.body(client.send(POST, "/api/owner/restaurants/" + restaurantId + "/tables", owner,
                Map.of("label", "T4")).andExpect(status().isCreated())).path("qrToken").asText();
        tandoor = fixtures.consentedChefOnStation(owner, restaurantId, "Tandoor");
        customer = client.registered("CUSTOMER").path("accessToken").asText();
    }

    Map<String, Object> order(String type) {
        Map<String, Object> body = new HashMap<>();
        body.put("restaurantId", restaurantId);
        body.put("orderType", type);
        body.put("items", List.of(Map.of("menuItemId", tikka, "quantity", 2), Map.of("menuItemId", dal, "quantity", 1)));
        if (type.equals("DINE_IN")) body.put("tableQrToken", tableQr);
        if (type.equals("DELIVERY")) body.put("deliveryAddress", Map.of("address", "4 Brigade Rd", "latitude", 12.9750, "longitude", 77.6060));
        return body;
    }

    ResultActions place(Map<String, Object> body) throws Exception {
        return client.send(POST, "/api/orders", customer, body);
    }

    JsonNode placed(Map<String, Object> body) throws Exception {
        return client.body(place(body).andExpect(status().isCreated()));
    }

    /** Placed and (for ONLINE) paid, so the restaurant can see it. */
    String visibleOrder(String type) throws Exception {
        JsonNode order = placed(order(type));
        String id = order.path("id").asText();
        if (order.path("paymentStatus").asText().equals("PENDING")) {
            pay(id, "pay_ok").andExpect(status().isOk());
        }
        return id;
    }

    ResultActions pay(String orderId, String paymentId) throws Exception {
        return client.send(POST, "/api/orders/" + orderId + "/payment/confirm", customer,
                Map.of("providerPaymentId", paymentId, "signature", "sig"));
    }

    ResultActions ownerSets(String orderId, String status) throws Exception {
        return ownerSets(orderId, Map.of("status", status));
    }

    ResultActions ownerSets(String orderId, Map<String, Object> body) throws Exception {
        return client.send(PATCH, "/api/owner/restaurants/" + restaurantId + "/orders/" + orderId + "/status", owner, body);
    }

    ResultActions chefSets(String orderId, Map<String, Object> body) throws Exception {
        return client.send(PATCH, "/api/chef/orders/" + orderId + "/status", tandoor.chefToken(), body);
    }

    ResultActions chefPrepares(String orderId) throws Exception {
        return chefSets(orderId, Map.of("status", "PREPARING", "stationId", tandoor.stationId()));
    }

    /** Drives an order from PLACED to {@code target} along the happy path. */
    void advance(String orderId, String... statuses) throws Exception {
        for (String s : statuses) {
            if (s.equals("PREPARING")) {
                chefPrepares(orderId).andExpect(status().isOk());
            } else {
                ownerSets(orderId, s).andExpect(status().isOk());
            }
        }
    }
}
