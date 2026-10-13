package com.virtualkitchen.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserListIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void adminsCanListUsersNewestFirstAndFilterByRole() throws Exception {
        AuthClient client = new AuthClient(mvc, json);
        JsonNode customer = client.registered("CUSTOMER");
        JsonNode owner = client.registered("RESTAURANT_OWNER");
        String admin = client.adminToken();

        JsonNode page = client.body(client.send(GET, "/api/admin/users?size=200", admin, null).andExpect(status().isOk()));
        assertThat(page.path("totalItems").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(page.path("items").get(0).path("id").asText()).isEqualTo(owner.path("user").path("id").asText());
        assertThat(page.toString()).doesNotContain("password");

        JsonNode owners = client.body(client.send(GET, "/api/admin/users?role=RESTAURANT_OWNER&size=200", admin, null));
        owners.path("items").forEach(u -> assertThat(u.path("role").asText()).isEqualTo("RESTAURANT_OWNER"));
        assertThat(owners.path("items").get(0).path("status").asText()).isEqualTo("PENDING_APPROVAL");

        client.send(GET, "/api/admin/users", customer.path("accessToken").asText(), null).andExpect(status().isForbidden());
        client.send(GET, "/api/admin/users?size=500", admin, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
