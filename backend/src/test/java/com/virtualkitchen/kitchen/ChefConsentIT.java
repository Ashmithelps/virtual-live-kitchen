package com.virtualkitchen.kitchen;

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

import java.util.Map;

import static com.virtualkitchen.support.AuthClient.PASSWORD;
import static com.virtualkitchen.support.AuthClient.randomPhone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChefConsentIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    AuthClient client;
    OwnerFixtures fixtures;

    String owner;
    String restaurantId;
    String chefId;
    String chefPhone;
    String stationId;
    String assignPath;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        fixtures = new OwnerFixtures(client);
        owner = client.approvedOwnerToken();
        restaurantId = fixtures.createRestaurant(owner).path("id").asText();
        chefPhone = randomPhone();
        chefId = fixtures.createChef(owner, restaurantId, chefPhone).path("id").asText();
        stationId = fixtures.createStation(owner, restaurantId, "Tandoor").path("id").asText();
        assignPath = "/api/owner/restaurants/" + restaurantId + "/stations/" + stationId + "/chefs/" + chefId;
    }

    @Test
    void unconsentedChefCannotBeAssigned() throws Exception {
        client.send(PUT, assignPath, owner, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Chef has not accepted the current recording consent and cannot be assigned to a station"));

        client.send(GET, "/api/owner/restaurants/" + restaurantId + "/stations", owner, null)
                .andExpect(jsonPath("$[0].chefs", hasSize(0)));
        client.send(GET, "/api/owner/restaurants/" + restaurantId + "/chefs/" + chefId, owner, null)
                .andExpect(jsonPath("$.recordingConsent").value(false));
    }

    @Test
    void chefMustAcceptCurrentConsentThenCanBeAssigned() throws Exception {
        String chef = client.loggedIn(chefPhone, PASSWORD).path("accessToken").asText();

        client.send(GET, "/api/chef/me", chef, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(true));
        JsonNode consent = client.body(client.send(GET, "/api/chef/consent", chef, null).andExpect(status().isOk()));
        assertThat(consent.path("version").asText()).isEqualTo("v1");
        assertThat(consent.path("text").asText()).contains("Kitchen Recording Consent");
        assertThat(consent.path("accepted").asBoolean()).isFalse();

        // Accepting a different text than the current one is refused.
        client.send(POST, "/api/chef/consent", chef, Map.of("consentVersion", "v1", "consentTextHash", "0".repeat(64)))
                .andExpect(status().isConflict());

        accept(chef, consent);
        client.send(GET, "/api/chef/me", chef, null).andExpect(jsonPath("$.consentRequired").value(false));

        client.send(PUT, assignPath, owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chefs[0].id").value(chefId));
        client.send(GET, "/api/chef/me", chef, null).andExpect(jsonPath("$.stations[0].name").value("Tandoor"));

        Map<String, Object> row = jdbc.queryForMap(
                "select consent_version, consent_text_hash, ip, accepted_at from chef_consents where chef_id = ?::uuid", chefId);
        assertThat(row.get("consent_version")).isEqualTo("v1");
        assertThat(row.get("consent_text_hash")).isEqualTo(consent.path("textHash").asText());
        assertThat(row.get("ip")).isNotNull();
    }

    @Test
    void withdrawalRemovesChefFromStationsAndBlocksReassignment() throws Exception {
        String chef = client.loggedIn(chefPhone, PASSWORD).path("accessToken").asText();
        accept(chef, client.body(client.send(GET, "/api/chef/consent", chef, null)));
        client.send(PUT, assignPath, owner, null).andExpect(status().isOk());

        client.send(POST, "/api/chef/consent/withdraw", chef, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false));

        client.send(GET, "/api/owner/restaurants/" + restaurantId + "/stations", owner, null)
                .andExpect(jsonPath("$[0].chefs", hasSize(0)));
        client.send(PUT, assignPath, owner, null).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
                "select count(*) from chef_consents where chef_id = ?::uuid and withdrawn_at is not null",
                Integer.class, chefId)).isEqualTo(1);

        // Re-accepting restores eligibility (and adds a new history row).
        accept(chef, client.body(client.send(GET, "/api/chef/consent", chef, null)));
        client.send(PUT, assignPath, owner, null).andExpect(status().isOk());
    }

    @Test
    void chefFromAnotherRestaurantCannotBeAssigned() throws Exception {
        String otherOwner = client.approvedOwnerToken();
        String otherRestaurant = fixtures.createRestaurant(otherOwner).path("id").asText();
        String otherChefPhone = randomPhone();
        String otherChef = fixtures.createChef(otherOwner, otherRestaurant, otherChefPhone).path("id").asText();
        String otherChefToken = client.loggedIn(otherChefPhone, PASSWORD).path("accessToken").asText();
        accept(otherChefToken, client.body(client.send(GET, "/api/chef/consent", otherChefToken, null)));

        client.send(PUT, "/api/owner/restaurants/" + restaurantId + "/stations/" + stationId + "/chefs/" + otherChef,
                owner, null).andExpect(status().isNotFound());
    }

    @Test
    void removedChefCannotLogIn() throws Exception {
        client.loggedIn(chefPhone, PASSWORD);

        client.send(DELETE, "/api/owner/restaurants/" + restaurantId + "/chefs/" + chefId, owner, null)
                .andExpect(status().isNoContent());

        client.login(chefPhone, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account is disabled"));
        client.send(GET, "/api/owner/restaurants/" + restaurantId + "/chefs", owner, null)
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void customersAndOwnersCannotUseChefEndpoints() throws Exception {
        client.send(GET, "/api/chef/consent", owner, null).andExpect(status().isForbidden());
        String customer = client.registered("CUSTOMER").path("accessToken").asText();
        client.send(POST, "/api/chef/consent/withdraw", customer, null).andExpect(status().isForbidden());
    }

    private void accept(String chefToken, JsonNode consent) throws Exception {
        client.send(POST, "/api/chef/consent", chefToken, Map.of(
                        "consentVersion", consent.path("version").asText(),
                        "consentTextHash", consent.path("textHash").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }
}
