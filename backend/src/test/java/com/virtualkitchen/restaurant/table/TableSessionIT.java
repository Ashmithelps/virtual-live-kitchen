package com.virtualkitchen.restaurant.table;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import com.virtualkitchen.support.OwnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.virtualkitchen.support.OwnerFixtures.ALWAYS_OPEN;
import static com.virtualkitchen.support.OwnerFixtures.itemBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TableSessionIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    AuthClient client;
    OwnerFixtures fixtures;
    String owner;
    String restaurantId;
    String slug;
    String itemId;
    String customer;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        fixtures = new OwnerFixtures(client);
        owner = client.approvedOwnerToken();
        JsonNode restaurant = fixtures.createRestaurant(owner, OwnerFixtures.restaurantBody("Cafe Table",
                OwnerFixtures.randomCity(), List.of(), List.of("DINE_IN", "TAKEAWAY"), ALWAYS_OPEN));
        restaurantId = restaurant.path("id").asText();
        slug = restaurant.path("slug").asText();
        String category = fixtures.createCategory(owner, restaurantId, "Coffee").path("id").asText();
        itemId = fixtures.createItem(owner, restaurantId, itemBody(category, "Filter Coffee", 6000, true)).path("id").asText();
        customer = client.registered("CUSTOMER").path("accessToken").asText();
    }

    @Test
    void lookupReturnsSlugLabelMenuAndSession() throws Exception {
        fixtures.approve(restaurantId);
        String qr = createTable("T4").path("qrToken").asText();

        client.send(GET, "/api/tables/" + qr, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantSlug").value(slug))
                .andExpect(jsonPath("$.tableLabel").value("T4"))
                .andExpect(jsonPath("$.menu[0].items[0].name").value("Filter Coffee"))
                .andExpect(jsonPath("$.tableSessionToken").isNotEmpty())
                .andExpect(jsonPath("$.tableSessionExpiresAt").isNotEmpty());
    }

    @Test
    void tablesOfUnapprovedRestaurantsAreNotFound() throws Exception {
        String qr = createTable("T1").path("qrToken").asText();
        client.send(GET, "/api/tables/" + qr, null, null).andExpect(status().isNotFound());
    }

    @Test
    void dineInOrderIsLockedToTheScannedTable() throws Exception {
        fixtures.approve(restaurantId);
        createTable("T1");
        String t2 = createTable("T2").path("qrToken").asText();
        String session = scan(t2);

        Map<String, Object> order = dineIn(session);
        // Extra table fields are not part of the API; the table can only come from the session.
        order.put("tableId", "00000000-0000-0000-0000-000000000000");
        order.put("tableLabel", "T1");
        client.send(POST, "/api/orders", customer, order)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableLabel").value("T2"));
    }

    @Test
    void regeneratingTheQrInvalidatesOldCodeAndSessions() throws Exception {
        fixtures.approve(restaurantId);
        JsonNode table = createTable("T1");
        String oldQr = table.path("qrToken").asText();
        String oldSession = scan(oldQr);

        String newQr = client.body(client.send(POST, "/api/owner/restaurants/" + restaurantId + "/tables/"
                + table.path("id").asText() + "/qr-token", owner, null).andExpect(status().isOk())).path("qrToken").asText();
        assertThat(newQr).isNotEqualTo(oldQr);

        client.send(GET, "/api/tables/" + oldQr, null, null).andExpect(status().isNotFound());
        client.send(POST, "/api/orders", customer, dineIn(oldSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This table's QR code has been replaced; please scan the new code"));

        client.send(POST, "/api/orders", customer, dineIn(scan(newQr))).andExpect(status().isCreated());
    }

    @Test
    void sessionsCannotBeForgedOrReused() throws Exception {
        fixtures.approve(restaurantId);
        String session = scan(createTable("T1").path("qrToken").asText());

        // Tampered
        client.send(POST, "/api/orders", customer, dineIn(session.substring(0, session.length() - 3) + "abc"))
                .andExpect(status().isBadRequest());
        // An access token is not a table session, and a table session is not an access token.
        client.send(POST, "/api/orders", customer, dineIn(customer)).andExpect(status().isBadRequest());
        client.send(GET, "/api/me", session, null).andExpect(status().isUnauthorized());

        // A session from another restaurant can't be used here.
        String otherOwner = client.approvedOwnerToken();
        String other = fixtures.createRestaurant(otherOwner, OwnerFixtures.restaurantBody("Other",
                OwnerFixtures.randomCity(), List.of(), List.of("DINE_IN"), ALWAYS_OPEN)).path("id").asText();
        fixtures.approve(other);
        String otherQr = client.body(client.send(POST, "/api/owner/restaurants/" + other + "/tables", otherOwner,
                Map.of("label", "X1"))).path("qrToken").asText();
        client.send(POST, "/api/orders", customer, dineIn(scan(otherQr)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Table session belongs to a different restaurant"));
    }

    @Test
    void deletedTableSessionIsRejected() throws Exception {
        fixtures.approve(restaurantId);
        JsonNode table = createTable("T1");
        String session = scan(table.path("qrToken").asText());
        client.send(org.springframework.http.HttpMethod.DELETE, "/api/owner/restaurants/" + restaurantId + "/tables/"
                + table.path("id").asText(), owner, null).andExpect(status().isNoContent());

        client.send(POST, "/api/orders", customer, dineIn(session)).andExpect(status().isConflict());
    }

    private JsonNode createTable(String label) throws Exception {
        return client.body(client.send(POST, "/api/owner/restaurants/" + restaurantId + "/tables", owner,
                Map.of("label", label)).andExpect(status().isCreated()));
    }

    private String scan(String qr) throws Exception {
        return client.body(client.send(GET, "/api/tables/" + qr, null, null).andExpect(status().isOk()))
                .path("tableSessionToken").asText();
    }

    private Map<String, Object> dineIn(String session) {
        Map<String, Object> body = new HashMap<>();
        body.put("restaurantId", restaurantId);
        body.put("orderType", "DINE_IN");
        body.put("paymentMethod", "PAY_LATER");
        body.put("tableSessionToken", session);
        body.put("items", List.of(Map.of("menuItemId", itemId, "quantity", 1)));
        return body;
    }
}
