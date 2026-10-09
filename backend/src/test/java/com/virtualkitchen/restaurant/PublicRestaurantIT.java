package com.virtualkitchen.restaurant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import com.virtualkitchen.support.OwnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.virtualkitchen.support.OwnerFixtures.ALWAYS_OPEN;
import static com.virtualkitchen.support.OwnerFixtures.itemBody;
import static com.virtualkitchen.support.OwnerFixtures.restaurantBody;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicRestaurantIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    AuthClient client;
    OwnerFixtures fixtures;
    String owner;
    String city;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        fixtures = new OwnerFixtures(client);
        owner = client.approvedOwnerToken();
        city = OwnerFixtures.randomCity(); // isolates each test's listing
    }

    @Test
    void onlyAdminApprovedRestaurantsAreListed() throws Exception {
        String id = fixtures.createRestaurant(owner, restaurantBody("Dosa Corner", city, List.of("South Indian"),
                List.of("TAKEAWAY"), ALWAYS_OPEN)).path("id").asText();

        client.send(GET, "/api/restaurants?city=" + city, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        // The owner cannot approve their own restaurant.
        client.send(POST, "/api/admin/restaurants/" + id + "/approve", owner, null).andExpect(status().isForbidden());

        fixtures.approve(id);
        client.send(GET, "/api/restaurants?city=" + city.toUpperCase(), null, null)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Dosa Corner"))
                .andExpect(jsonPath("$.items[0].cuisineTags[0]").value("south-indian"))
                .andExpect(jsonPath("$.items[0].openNow").value(true));

        client.send(POST, "/api/admin/restaurants/" + id + "/suspend", client.adminToken(), null)
                .andExpect(status().isOk());
        client.send(GET, "/api/restaurants?city=" + city, null, null).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void filtersByCuisineVegOnlyOpenNowAndOrderType() throws Exception {
        String vegDelivery = active("Green Leaf", List.of("South Indian"), List.of("DELIVERY", "TAKEAWAY"), ALWAYS_OPEN);
        String nonVegDineIn = active("Kebab House", List.of("North Indian", "Mughlai"), List.of("DINE_IN"), ALWAYS_OPEN);
        String closed = active("Night Owl", List.of("North Indian"), List.of("DINE_IN", "TAKEAWAY"), List.of());

        addItem(vegDelivery, "Masala Dosa", true);
        addItem(nonVegDineIn, "Seekh Kebab", false);
        addItem(nonVegDineIn, "Dal Makhani", true);
        addItem(closed, "Paneer Butter Masala", true);

        String base = "/api/restaurants?city=" + city;
        client.send(GET, base, null, null).andExpect(jsonPath("$.totalItems").value(3));
        client.send(GET, base + "&cuisine=North Indian", null, null)
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Kebab House", "Night Owl")));
        client.send(GET, base + "&vegOnly=true", null, null)
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Green Leaf", "Night Owl")));
        client.send(GET, base + "&openNow=true", null, null)
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Green Leaf", "Kebab House")));
        client.send(GET, base + "&orderType=DELIVERY", null, null)
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Green Leaf")));
        client.send(GET, base + "&orderType=DINE_IN&vegOnly=true", null, null)
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Night Owl")));
        client.send(GET, base + "&size=2&page=1", null, null)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void detailBySlugIncludesMenu() throws Exception {
        String id = active("Biryani Blues", List.of("Biryani"), List.of("TAKEAWAY"), ALWAYS_OPEN);
        String slug = client.body(client.send(GET, "/api/owner/restaurants/" + id, owner, null)).path("slug").asText();
        String category = fixtures.createCategory(owner, id, "Biryani").path("id").asText();
        fixtures.createCategory(owner, id, "Empty category");
        fixtures.createItem(owner, id, itemBody(category, "Hyderabadi Chicken Biryani", 32900, false));

        client.send(GET, "/api/restaurants/" + slug, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Biryani Blues"))
                .andExpect(jsonPath("$.openNow").value(true))
                .andExpect(jsonPath("$.menu", hasSize(1)))
                .andExpect(jsonPath("$.menu[0].items[0].pricePaise").value(32900))
                .andExpect(jsonPath("$.menu[0].items[0].veg").value(false));
    }

    @Test
    void pendingRestaurantDetailIsHidden() throws Exception {
        String id = fixtures.createRestaurant(owner).path("id").asText();
        String slug = client.body(client.send(GET, "/api/owner/restaurants/" + id, owner, null)).path("slug").asText();

        client.send(GET, "/api/restaurants/" + slug, null, null).andExpect(status().isNotFound());
    }

    @Test
    void slugsAreUniqueAndValidated() throws Exception {
        var body = restaurantBody("Same Name", city, List.of(), List.of("TAKEAWAY"), ALWAYS_OPEN);
        String first = fixtures.createRestaurant(owner, body).path("slug").asText();
        String second = fixtures.createRestaurant(owner, body).path("slug").asText();
        org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first).startsWith(first);

        body.put("slug", first);
        client.send(POST, "/api/owner/restaurants", owner, body).andExpect(status().isConflict());
        body.put("slug", "Not A Slug!");
        client.send(POST, "/api/owner/restaurants", owner, body).andExpect(status().isBadRequest());
    }

    @Test
    void deliveryRequiresRadius() throws Exception {
        var body = restaurantBody("Fast Wheels", city, List.of(), List.of("DELIVERY"), ALWAYS_OPEN);
        body.remove("deliveryRadiusKm");
        client.send(POST, "/api/owner/restaurants", owner, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("deliveryRadiusKm is required when DELIVERY is supported"));
    }

    @Test
    void cityIsRequired() throws Exception {
        client.send(GET, "/api/restaurants", null, null).andExpect(status().isBadRequest());
    }

    private String active(String name, List<String> cuisines, List<String> orderTypes,
                          List<java.util.Map<String, String>> hours) throws Exception {
        String id = fixtures.createRestaurant(owner, restaurantBody(name, city, cuisines, orderTypes, hours))
                .path("id").asText();
        fixtures.approve(id);
        return id;
    }

    private void addItem(String restaurantId, String name, boolean veg) throws Exception {
        String category = fixtures.createCategory(owner, restaurantId, "Mains " + name).path("id").asText();
        fixtures.createItem(owner, restaurantId, itemBody(category, name, 19900, veg));
    }
}
