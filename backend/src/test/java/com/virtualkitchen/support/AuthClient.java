package com.virtualkitchen.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Small helper around MockMvc for the auth endpoints. */
public class AuthClient {

    public static final String ADMIN_PHONE = "+910000000001";
    public static final String ADMIN_PASSWORD = "test-admin-password";
    public static final String PASSWORD = "correct-horse-1";

    private final MockMvc mvc;
    private final ObjectMapper json;

    public AuthClient(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    /** Unique per call, so tests never collide on the shared database or rate limiter. */
    public static String randomPhone() {
        return "+919" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
    }

    public ResultActions register(String phone, String role) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Test User");
        body.put("phone", phone);
        body.put("password", PASSWORD);
        if (role != null) {
            body.put("role", role);
        }
        return postJson("/api/auth/register", body);
    }

    public ResultActions login(String phone, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("phone", phone, "password", password));
    }

    public ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    public ResultActions logout(String refreshToken) throws Exception {
        return postJson("/api/auth/logout", Map.of("refreshToken", refreshToken));
    }

    public ResultActions getWithToken(String path, String accessToken) throws Exception {
        return mvc.perform(withBearer(get(path), accessToken));
    }

    public ResultActions postWithToken(String path, String accessToken) throws Exception {
        return mvc.perform(withBearer(post(path), accessToken));
    }

    /** Registers a user and returns the parsed AuthResponse. */
    public JsonNode registered(String role) throws Exception {
        return body(register(randomPhone(), role).andExpect(status().isCreated()));
    }

    public JsonNode loggedIn(String phone, String password) throws Exception {
        return body(login(phone, password).andExpect(status().isOk()));
    }

    public JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Registers a RESTAURANT_OWNER, has the seeded admin approve them, and returns their access token. */
    public String approvedOwnerToken() throws Exception {
        JsonNode owner = registered("RESTAURANT_OWNER");
        String adminToken = loggedIn(ADMIN_PHONE, ADMIN_PASSWORD).path("accessToken").asText();
        postWithToken("/api/admin/owners/" + owner.path("user").path("id").asText() + "/approve", adminToken)
                .andExpect(status().isOk());
        return loggedIn(owner.path("user").path("phone").asText(), PASSWORD).path("accessToken").asText();
    }

    public String adminToken() throws Exception {
        return loggedIn(ADMIN_PHONE, ADMIN_PASSWORD).path("accessToken").asText();
    }

    /** Any method, optional bearer token, optional JSON body. */
    public ResultActions send(HttpMethod method, String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder request = withBearer(request(method, path), token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    public ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
