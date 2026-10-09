package com.virtualkitchen.restaurant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import com.virtualkitchen.support.OwnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static com.virtualkitchen.support.OwnerFixtures.itemBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Presigned upload against a real S3-compatible server (RustFS): owner PUTs bytes, attaches the key, customers can read it. */
class MediaUploadIT extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    AuthClient client;
    OwnerFixtures fixtures;
    String owner;
    String restaurantId;
    String categoryId;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        fixtures = new OwnerFixtures(client);
        owner = client.approvedOwnerToken();
        restaurantId = fixtures.createRestaurant(owner).path("id").asText();
        categoryId = fixtures.createCategory(owner, restaurantId, "Mains").path("id").asText();
    }

    @Test
    void presignedUploadThenAttachToMenuItem() throws Exception {
        JsonNode upload = requestUpload(restaurantId, "image/png");
        String key = upload.path("objectKey").asText();
        assertThat(key).startsWith("restaurants/" + restaurantId + "/menu-items/").endsWith(".png");

        HttpResponse<Void> put = http.send(HttpRequest.newBuilder(URI.create(upload.path("uploadUrl").asText()))
                .header("Content-Type", "image/png")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(PNG)).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);

        Map<String, Object> body = itemBody(categoryId, "Butter Naan", 6000, true);
        body.put("imageKey", key);
        fixtures.createItem(owner, restaurantId, body);
        fixtures.approve(restaurantId);
        String slug = client.body(client.send(GET, "/api/owner/restaurants/" + restaurantId, owner, null))
                .path("slug").asText();

        JsonNode item = client.body(client.send(GET, "/api/restaurants/" + slug, null, null))
                .path("menu").get(0).path("items").get(0);
        assertThat(item.path("imageKey").asText()).isEqualTo(key);
        HttpResponse<byte[]> get = http.send(HttpRequest.newBuilder(URI.create(item.path("imageUrl").asText())).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.body()).isEqualTo(PNG);
    }

    @Test
    void keysFromAnotherRestaurantOrHandWrittenAreRejected() throws Exception {
        String otherRestaurant = fixtures.createRestaurant(owner).path("id").asText();
        String foreignKey = requestUpload(otherRestaurant, "image/jpeg").path("objectKey").asText();

        Map<String, Object> body = itemBody(categoryId, "Roti", 3000, true);
        body.put("imageKey", foreignKey);
        client.send(POST, "/api/owner/restaurants/" + restaurantId + "/menu/items", owner, body)
                .andExpect(status().isBadRequest());
        body.put("imageKey", "restaurants/" + restaurantId + "/menu-items/../../secrets.png");
        client.send(POST, "/api/owner/restaurants/" + restaurantId + "/menu/items", owner, body)
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyImageContentTypesAreAccepted() throws Exception {
        client.send(POST, "/api/owner/restaurants/" + restaurantId + "/uploads", owner,
                        Map.of("purpose", "MENU_ITEM_IMAGE", "contentType", "text/html"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chefPhotoKeyMustUseChefPurpose() throws Exception {
        String menuKey = requestUpload(restaurantId, "image/png").path("objectKey").asText();
        client.send(POST, "/api/owner/restaurants/" + restaurantId + "/chefs", owner, Map.of(
                        "name", "Chef A", "phone", AuthClient.randomPhone(), "initialPassword", AuthClient.PASSWORD,
                        "photoKey", menuKey))
                .andExpect(status().isBadRequest());
    }

    private JsonNode requestUpload(String restaurant, String contentType) throws Exception {
        return client.body(client.send(POST, "/api/owner/restaurants/" + restaurant + "/uploads", owner,
                Map.of("purpose", "MENU_ITEM_IMAGE", "contentType", contentType)).andExpect(status().isCreated()));
    }
}
