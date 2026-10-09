package com.virtualkitchen.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Builds restaurants, menus, chefs and stations through the owner API. */
public class OwnerFixtures {

    /** Every day, 00:00–00:00 = open 24 hours. */
    public static final List<Map<String, String>> ALWAYS_OPEN = java.util.Arrays.stream(java.time.DayOfWeek.values())
            .map(d -> Map.of("day", d.name(), "opens", "00:00", "closes", "00:00"))
            .toList();

    private final AuthClient client;

    public OwnerFixtures(AuthClient client) {
        this.client = client;
    }

    public static String randomCity() {
        return "City-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static Map<String, Object> restaurantBody(String name, String city, List<String> cuisines,
                                                     List<String> orderTypes, List<Map<String, String>> hours) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("address", "12 MG Road");
        body.put("city", city);
        body.put("latitude", 12.9716);
        body.put("longitude", 77.5946);
        body.put("cuisineTags", cuisines);
        body.put("openingHours", hours);
        body.put("orderTypes", orderTypes);
        if (orderTypes.contains("DELIVERY")) {
            body.put("deliveryRadiusKm", 5.0);
        }
        return body;
    }

    public JsonNode createRestaurant(String ownerToken, Map<String, Object> body) throws Exception {
        return client.body(client.send(HttpMethod.POST, "/api/owner/restaurants", ownerToken, body)
                .andExpect(status().isCreated()));
    }

    public JsonNode createRestaurant(String ownerToken) throws Exception {
        return createRestaurant(ownerToken, restaurantBody("Spice Garden", randomCity(), List.of("North Indian"),
                List.of("DINE_IN", "TAKEAWAY"), ALWAYS_OPEN));
    }

    public JsonNode createCategory(String ownerToken, String restaurantId, String name) throws Exception {
        return client.body(client.send(HttpMethod.POST, "/api/owner/restaurants/" + restaurantId + "/menu/categories",
                ownerToken, Map.of("name", name)).andExpect(status().isCreated()));
    }

    public static Map<String, Object> itemBody(String categoryId, String name, long pricePaise, boolean veg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", name);
        body.put("pricePaise", pricePaise);
        body.put("veg", veg);
        return body;
    }

    public JsonNode createItem(String ownerToken, String restaurantId, Map<String, Object> body) throws Exception {
        return client.body(client.send(HttpMethod.POST, "/api/owner/restaurants/" + restaurantId + "/menu/items",
                ownerToken, body).andExpect(status().isCreated()));
    }

    public JsonNode approve(String restaurantId) throws Exception {
        return client.body(client.send(HttpMethod.POST, "/api/admin/restaurants/" + restaurantId + "/approve",
                client.adminToken(), null).andExpect(status().isOk()));
    }

    public JsonNode createChef(String ownerToken, String restaurantId, String phone) throws Exception {
        return client.body(client.send(HttpMethod.POST, "/api/owner/restaurants/" + restaurantId + "/chefs", ownerToken,
                Map.of("name", "Chef Ravi", "phone", phone, "initialPassword", AuthClient.PASSWORD,
                        "specialty", "Tandoor")).andExpect(status().isCreated()));
    }

    /** Chef with accepted consent, assigned to a new station. */
    public record StaffedStation(String chefId, String chefToken, String stationId) {
    }

    public StaffedStation consentedChefOnStation(String ownerToken, String restaurantId, String stationName) throws Exception {
        String phone = AuthClient.randomPhone();
        String chefId = createChef(ownerToken, restaurantId, phone).path("id").asText();
        String chefToken = client.loggedIn(phone, AuthClient.PASSWORD).path("accessToken").asText();
        JsonNode consent = client.body(client.send(HttpMethod.GET, "/api/chef/consent", chefToken, null));
        client.send(HttpMethod.POST, "/api/chef/consent", chefToken, Map.of(
                        "consentVersion", consent.path("version").asText(),
                        "consentTextHash", consent.path("textHash").asText()))
                .andExpect(status().isOk());
        String stationId = createStation(ownerToken, restaurantId, stationName).path("id").asText();
        client.send(HttpMethod.PUT, "/api/owner/restaurants/" + restaurantId + "/stations/" + stationId + "/chefs/" + chefId,
                ownerToken, null).andExpect(status().isOk());
        return new StaffedStation(chefId, chefToken, stationId);
    }

    public JsonNode createStation(String ownerToken, String restaurantId, String name) throws Exception {
        return client.body(client.send(HttpMethod.POST, "/api/owner/restaurants/" + restaurantId + "/stations",
                ownerToken, Map.of("name", name)).andExpect(status().isCreated()));
    }
}
