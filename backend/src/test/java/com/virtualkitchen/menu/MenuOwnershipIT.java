package com.virtualkitchen.menu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import com.virtualkitchen.support.OwnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static com.virtualkitchen.support.OwnerFixtures.itemBody;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An owner can only touch their own restaurant's menu (and other owner-scoped resources). */
class MenuOwnershipIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    AuthClient client;
    OwnerFixtures fixtures;

    String ownerA;
    String ownerB;
    String restaurantA;
    String restaurantB;
    String categoryA;
    String itemA;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        fixtures = new OwnerFixtures(client);

        ownerA = client.approvedOwnerToken();
        ownerB = client.approvedOwnerToken();
        restaurantA = fixtures.createRestaurant(ownerA).path("id").asText();
        restaurantB = fixtures.createRestaurant(ownerB).path("id").asText();
        categoryA = fixtures.createCategory(ownerA, restaurantA, "Starters").path("id").asText();
        itemA = fixtures.createItem(ownerA, restaurantA, itemBody(categoryA, "Paneer Tikka", 24900, true))
                .path("id").asText();
    }

    @Test
    void ownerCannotEditAnotherRestaurantsMenu() throws Exception {
        String itemPath = "/api/owner/restaurants/" + restaurantA + "/menu/items/" + itemA;
        Map<String, Object> hijack = itemBody(categoryA, "Hijacked", 100, false);

        client.send(GET, "/api/owner/restaurants/" + restaurantA + "/menu", ownerB, null)
                .andExpect(status().isNotFound());
        client.send(PUT, itemPath, ownerB, hijack)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Restaurant not found"));
        client.send(PATCH, itemPath + "/availability", ownerB, Map.of("available", false))
                .andExpect(status().isNotFound());
        client.send(DELETE, itemPath, ownerB, null).andExpect(status().isNotFound());
        client.send(POST, "/api/owner/restaurants/" + restaurantA + "/menu/categories", ownerB, Map.of("name", "Mine now"))
                .andExpect(status().isNotFound());
        client.send(POST, "/api/owner/restaurants/" + restaurantA + "/menu/items", ownerB, hijack)
                .andExpect(status().isNotFound());
        client.send(DELETE, "/api/owner/restaurants/" + restaurantA + "/menu/categories/" + categoryA, ownerB, null)
                .andExpect(status().isNotFound());

        assertItemUnchanged();
    }

    @Test
    void ownerCannotReachAnotherRestaurantsItemThroughTheirOwnRestaurant() throws Exception {
        String ownPathToForeignItem = "/api/owner/restaurants/" + restaurantB + "/menu/items/" + itemA;

        client.send(PUT, ownPathToForeignItem, ownerB, itemBody(categoryA, "Hijacked", 100, false))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Menu item not found"));
        client.send(DELETE, ownPathToForeignItem, ownerB, null).andExpect(status().isNotFound());

        // Nor file a new item in restaurant B under restaurant A's category.
        client.send(POST, "/api/owner/restaurants/" + restaurantB + "/menu/items", ownerB,
                        itemBody(categoryA, "Sneaky", 100, true))
                .andExpect(status().isBadRequest());

        assertItemUnchanged();
    }

    @Test
    void ownerCannotTouchAnotherRestaurantsDetailsTablesStationsOrChefs() throws Exception {
        String base = "/api/owner/restaurants/" + restaurantA;
        client.send(GET, base, ownerB, null).andExpect(status().isNotFound());
        client.send(PUT, base, ownerB, OwnerFixtures.restaurantBody("Renamed", "X", java.util.List.of(),
                java.util.List.of("TAKEAWAY"), java.util.List.of())).andExpect(status().isNotFound());
        client.send(POST, base + "/tables", ownerB, Map.of("label", "T1")).andExpect(status().isNotFound());
        client.send(POST, base + "/stations", ownerB, Map.of("name", "Tandoor")).andExpect(status().isNotFound());
        client.send(GET, base + "/chefs", ownerB, null).andExpect(status().isNotFound());
        client.send(POST, base + "/uploads", ownerB, Map.of("purpose", "MENU_ITEM_IMAGE", "contentType", "image/png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerEndpointsRejectNonOwners() throws Exception {
        String customer = client.registered("CUSTOMER").path("accessToken").asText();
        String pendingOwner = client.registered("RESTAURANT_OWNER").path("accessToken").asText();
        String menuPath = "/api/owner/restaurants/" + restaurantA + "/menu";

        client.send(GET, menuPath, null, null).andExpect(status().isUnauthorized());
        client.send(GET, menuPath, customer, null).andExpect(status().isForbidden());
        client.send(GET, menuPath, pendingOwner, null).andExpect(status().isForbidden());
        client.send(POST, "/api/owner/restaurants", pendingOwner, OwnerFixtures.restaurantBody("X", "Y",
                java.util.List.of(), java.util.List.of("TAKEAWAY"), java.util.List.of())).andExpect(status().isForbidden());
    }

    @Test
    void ownerCanEditOwnMenu() throws Exception {
        String itemPath = "/api/owner/restaurants/" + restaurantA + "/menu/items/" + itemA;
        client.send(PUT, itemPath, ownerA, itemBody(categoryA, "Paneer Tikka (6 pcs)", 26900, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricePaise").value(26900));
        client.send(PATCH, itemPath + "/availability", ownerA, Map.of("available", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        client.send(DELETE, "/api/owner/restaurants/" + restaurantA + "/menu/categories/" + categoryA, ownerA, null)
                .andExpect(status().isConflict());
    }

    private void assertItemUnchanged() throws Exception {
        JsonNode menu = client.body(client.send(GET, "/api/owner/restaurants/" + restaurantA + "/menu", ownerA, null)
                .andExpect(status().isOk()));
        JsonNode item = menu.get(0).path("items").get(0);
        org.assertj.core.api.Assertions.assertThat(item.path("id").asText()).isEqualTo(itemA);
        org.assertj.core.api.Assertions.assertThat(item.path("name").asText()).isEqualTo("Paneer Tikka");
        org.assertj.core.api.Assertions.assertThat(item.path("pricePaise").asLong()).isEqualTo(24900);
        org.assertj.core.api.Assertions.assertThat(item.path("available").asBoolean()).isTrue();
    }
}
