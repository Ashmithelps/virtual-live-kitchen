package com.virtualkitchen.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static com.virtualkitchen.support.AuthClient.PASSWORD;
import static com.virtualkitchen.support.AuthClient.randomPhone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    AuthClient client;

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, json);
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void registerCustomer_returnsTokensAndActiveCustomer() throws Exception {
        String phone = randomPhone();

        client.register(phone, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.user.phone").value(phone))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void register_storesBcryptHashNotPassword() throws Exception {
        String phone = randomPhone();
        client.register(phone, null).andExpect(status().isCreated());

        String hash = jdbc.queryForObject("select password_hash from users where phone = ?", String.class, phone);
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void registerOwner_startsPendingApproval() throws Exception {
        client.register(randomPhone(), "RESTAURANT_OWNER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("RESTAURANT_OWNER"))
                .andExpect(jsonPath("$.user.status").value("PENDING_APPROVAL"));
    }

    @Test
    void register_rejectsChefAndAdminSelfRegistration() throws Exception {
        client.register(randomPhone(), "CHEF").andExpect(status().isBadRequest());
        client.register(randomPhone(), "ADMIN").andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicatePhone_returns409() throws Exception {
        String phone = randomPhone();
        client.register(phone, null).andExpect(status().isCreated());

        client.register(phone, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phone number is already registered"));
    }

    @Test
    void register_invalidBody_returnsStandardErrorShape() throws Exception {
        client.postJson("/api/auth/register", Map.of("name", "", "phone", "98765", "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("phone")))
                .andExpect(jsonPath("$.message", containsString("password")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_withCorrectPassword_returnsTokens() throws Exception {
        String phone = randomPhone();
        client.register(phone, null).andExpect(status().isCreated());

        JsonNode body = client.loggedIn(phone, PASSWORD);

        assertThat(body.path("accessToken").asText()).isNotBlank();
        assertThat(body.path("user").path("phone").asText()).isEqualTo(phone);
        client.getWithToken("/api/me", body.path("accessToken").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(phone));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String phone = randomPhone();
        client.register(phone, null).andExpect(status().isCreated());

        client.login(phone, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid phone number or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void login_withUnknownPhone_returnsSame401AsWrongPassword() throws Exception {
        client.login(randomPhone(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid phone number or password"));
    }

    @Test
    void login_isRateLimitedPerPhone() throws Exception {
        String phone = randomPhone();
        client.register(phone, null).andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            client.login(phone, "wrong-password").andExpect(status().isUnauthorized());
        }

        // Even the correct password is refused once the bucket is empty.
        client.login(phone, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        // Other phones are unaffected.
        String other = randomPhone();
        client.register(other, null).andExpect(status().isCreated());
        client.login(other, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void login_successResetsRateLimit() throws Exception {
        String phone = randomPhone();
        client.register(phone, null).andExpect(status().isCreated());

        for (int i = 0; i < 4; i++) {
            client.login(phone, "wrong-password").andExpect(status().isUnauthorized());
        }
        client.login(phone, PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            client.login(phone, "wrong-password").andExpect(status().isUnauthorized());
        }
        client.login(phone, PASSWORD).andExpect(status().isOk());
    }

    // ── refresh & logout ─────────────────────────────────────────────────────

    @Test
    void refresh_rotatesToken_andOldTokenCannotBeReused() throws Exception {
        JsonNode registered = client.registered(null);
        String original = registered.path("refreshToken").asText();

        JsonNode refreshed = client.body(client.refresh(original)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken", not(original))));
        String rotated = refreshed.path("refreshToken").asText();
        client.getWithToken("/api/me", refreshed.path("accessToken").asText()).andExpect(status().isOk());

        client.refresh(original).andExpect(status().isUnauthorized());
        // Reusing a rotated token is treated as theft: the whole chain is revoked.
        client.refresh(rotated).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_storesOnlyHashedToken() throws Exception {
        JsonNode registered = client.registered(null);
        String raw = registered.path("refreshToken").asText();
        String userId = registered.path("user").path("id").asText();

        String stored = jdbc.queryForObject(
                "select token_hash from refresh_tokens where user_id = ?::uuid", String.class, userId);
        assertThat(stored).hasSize(64).isNotEqualTo(raw);
    }

    @Test
    void refresh_withExpiredToken_returns401() throws Exception {
        JsonNode registered = client.registered(null);
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 minute' where user_id = ?::uuid",
                registered.path("user").path("id").asText());

        client.refresh(registered.path("refreshToken").asText())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token has expired"));
    }

    @Test
    void refresh_withUnknownToken_returns401() throws Exception {
        client.refresh("not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesRefreshToken_andIsIdempotent() throws Exception {
        String refreshToken = client.registered(null).path("refreshToken").asText();

        client.logout(refreshToken).andExpect(status().isNoContent());
        client.logout(refreshToken).andExpect(status().isNoContent());

        client.refresh(refreshToken).andExpect(status().isUnauthorized());
    }
}
