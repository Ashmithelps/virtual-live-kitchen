package com.virtualkitchen.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.auth.token.JwtProperties;
import com.virtualkitchen.auth.token.JwtService;
import com.virtualkitchen.auth.user.UserRepository;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.virtualkitchen.support.AuthClient.ADMIN_PASSWORD;
import static com.virtualkitchen.support.AuthClient.ADMIN_PHONE;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessControlIT extends IntegrationTest {

    private static final String PENDING_OWNERS = "/api/admin/owners/pending";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtProperties jwtProperties;
    @Autowired UserRepository userRepository;

    AuthClient client;

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, json);
    }

    // ── /api/me and token validity ───────────────────────────────────────────

    @Test
    void me_withoutToken_returns401() throws Exception {
        client.getWithToken("/api/me", null)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/me"));
    }

    @Test
    void me_withTamperedToken_returns401() throws Exception {
        String token = client.registered(null).path("accessToken").asText();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        client.getWithToken("/api/me", tampered)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token is invalid"));
    }

    @Test
    void me_withExpiredToken_returns401() throws Exception {
        UUID userId = UUID.fromString(client.registered(null).path("user").path("id").asText());
        // Same secret, but a clock 16 minutes in the past: the 15-minute token is already expired.
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofMinutes(16)), ZoneOffset.UTC);
        String expired = new JwtService(jwtProperties, past).issue(userRepository.findById(userId).orElseThrow()).token();

        client.getWithToken("/api/me", expired)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has expired"));
    }

    @Test
    void me_withTokenSignedByAnotherKey_returns401() throws Exception {
        UUID userId = UUID.fromString(client.registered(null).path("user").path("id").asText());
        var otherKey = new JwtProperties("a-completely-different-secret-of-32-bytes!", jwtProperties.issuer(),
                jwtProperties.accessTokenTtl(), jwtProperties.refreshTokenTtl());
        String forged = new JwtService(otherKey, Clock.systemUTC()).issue(userRepository.findById(userId).orElseThrow()).token();

        client.getWithToken("/api/me", forged).andExpect(status().isUnauthorized());
    }

    @Test
    void me_isAvailableToPendingOwner() throws Exception {
        String token = client.registered("RESTAURANT_OWNER").path("accessToken").asText();

        client.getWithToken("/api/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    // ── role-protected endpoint ──────────────────────────────────────────────

    @Test
    void adminEndpoint_withoutToken_returns401() throws Exception {
        client.getWithToken(PENDING_OWNERS, null).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_asCustomer_returns403() throws Exception {
        String token = client.registered("CUSTOMER").path("accessToken").asText();

        client.getWithToken(PENDING_OWNERS, token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value(PENDING_OWNERS));
    }

    @Test
    void adminEndpoint_asPendingOwner_returns403() throws Exception {
        String token = client.registered("RESTAURANT_OWNER").path("accessToken").asText();

        client.getWithToken(PENDING_OWNERS, token).andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_asSeededAdmin_returns200() throws Exception {
        String ownerId = client.registered("RESTAURANT_OWNER").path("user").path("id").asText();
        String adminToken = client.loggedIn(ADMIN_PHONE, ADMIN_PASSWORD).path("accessToken").asText();

        client.getWithToken(PENDING_OWNERS, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(ownerId)));
    }

    @Test
    void approvedOwner_becomesActiveOnNextRefresh() throws Exception {
        JsonNode owner = client.registered("RESTAURANT_OWNER");
        String ownerId = owner.path("user").path("id").asText();
        String adminToken = client.loggedIn(ADMIN_PHONE, ADMIN_PASSWORD).path("accessToken").asText();

        client.postWithToken("/api/admin/owners/" + ownerId + "/approve", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        client.postWithToken("/api/admin/owners/" + ownerId + "/approve", adminToken)
                .andExpect(status().isConflict());

        client.refresh(owner.path("refreshToken").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("ACTIVE"));
        client.getWithToken(PENDING_OWNERS, adminToken)
                .andExpect(jsonPath("$[*].id", not(hasItem(ownerId))));
    }

    @Test
    void approve_unknownUser_returns404() throws Exception {
        String adminToken = client.loggedIn(ADMIN_PHONE, ADMIN_PASSWORD).path("accessToken").asText();

        client.postWithToken("/api/admin/owners/" + UUID.randomUUID() + "/approve", adminToken)
                .andExpect(status().isNotFound());
    }
}
