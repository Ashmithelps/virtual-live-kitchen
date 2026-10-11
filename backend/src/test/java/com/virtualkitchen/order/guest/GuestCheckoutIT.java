package com.virtualkitchen.order.guest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import com.virtualkitchen.support.OwnerFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.virtualkitchen.support.AuthClient.randomPhone;
import static com.virtualkitchen.support.OwnerFixtures.ALWAYS_OPEN;
import static com.virtualkitchen.support.OwnerFixtures.itemBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestCheckoutIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate rest;

    AuthClient client;
    String owner;
    String restaurantId;
    String itemId;
    String session;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        OwnerFixtures fixtures = new OwnerFixtures(client);
        owner = client.approvedOwnerToken();
        restaurantId = fixtures.createRestaurant(owner, OwnerFixtures.restaurantBody("Guest Cafe",
                OwnerFixtures.randomCity(), List.of(), List.of("DINE_IN", "TAKEAWAY"), ALWAYS_OPEN)).path("id").asText();
        fixtures.approve(restaurantId);
        String category = fixtures.createCategory(owner, restaurantId, "Snacks").path("id").asText();
        itemId = fixtures.createItem(owner, restaurantId, itemBody(category, "Samosa", 3000, true)).path("id").asText();
        String qr = client.body(client.send(POST, "/api/owner/restaurants/" + restaurantId + "/tables", owner,
                Map.of("label", "T7"))).path("qrToken").asText();
        session = client.body(client.send(GET, "/api/tables/" + qr, null, null)).path("tableSessionToken").asText();
    }

    @Test
    void guestOrdersWithNameAndPhoneOnly() throws Exception {
        String phone = randomPhone();
        JsonNode response = client.body(guestOrder("Meera", phone, "PAY_LATER").andExpect(status().isCreated()));

        assertThat(response.path("order").path("orderType").asText()).isEqualTo("DINE_IN");
        assertThat(response.path("order").path("tableLabel").asText()).isEqualTo("T7");
        assertThat(response.path("guestToken").asText()).isNotBlank();
        assertThat(response.has("accessToken")).isFalse();

        Map<String, Object> user = jdbc.queryForMap("select role, status, password_hash, name from users where phone = ?", phone);
        assertThat(user.get("role")).isEqualTo("CUSTOMER");
        assertThat(user.get("password_hash")).isNull();
        assertThat(user.get("name")).isEqualTo("Meera");

        client.send(GET, "/api/owner/restaurants/" + restaurantId + "/orders", owner, null)
                .andExpect(jsonPath("$[*].id", hasItem(response.path("order").path("id").asText())));
    }

    @Test
    void repeatGuestReusesTheSameCustomer() throws Exception {
        String phone = randomPhone();
        guestOrder("Meera", phone, "PAY_LATER").andExpect(status().isCreated());
        guestOrder("Meera K", phone, "PAY_LATER").andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select count(*) from users where phone = ?", Integer.class, phone)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from orders o join users u on u.id = o.customer_id where u.phone = ?",
                Integer.class, phone)).isEqualTo(2);
    }

    @Test
    void existingRegisteredCustomerIsReusedButGuestGetsNoLogin() throws Exception {
        String phone = randomPhone();
        client.register(phone, null).andExpect(status().isCreated());
        String customerToken = client.loggedIn(phone, AuthClient.PASSWORD).path("accessToken").asText();

        JsonNode response = client.body(guestOrder("Someone", phone, "PAY_LATER").andExpect(status().isCreated()));
        String orderId = response.path("order").path("id").asText();

        // The real account holder sees it in their history; the guest token is not a login.
        client.send(GET, "/api/orders/" + orderId, customerToken, null).andExpect(status().isOk());
        client.send(GET, "/api/me", response.path("guestToken").asText(), null).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select name from users where phone = ?", String.class, phone)).isEqualTo("Test User");
    }

    @Test
    void guestTokenOnlyOpensItsOwnOrder() throws Exception {
        JsonNode first = client.body(guestOrder("A", randomPhone(), "ONLINE").andExpect(status().isCreated()));
        JsonNode second = client.body(guestOrder("B", randomPhone(), "ONLINE").andExpect(status().isCreated()));
        String firstId = first.path("order").path("id").asText();
        String firstToken = first.path("guestToken").asText();

        withGuestToken(MockMvcRequestBuilders.get("/api/guest/orders/" + firstId), firstToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        withGuestToken(MockMvcRequestBuilders.get("/api/guest/orders/" + second.path("order").path("id").asText()), firstToken)
                .andExpect(status().isUnauthorized());
        withGuestToken(MockMvcRequestBuilders.get("/api/guest/orders/" + firstId), null).andExpect(status().isUnauthorized());
        withGuestToken(MockMvcRequestBuilders.get("/api/guest/orders/" + firstId), session).andExpect(status().isUnauthorized());

        // Start checkout, pay, then cancel (refund) with the same token.
        String providerOrderId = client.body(withGuestToken(MockMvcRequestBuilders.post("/api/guest/orders/" + firstId + "/payment"), firstToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prefillName").value("A"))).path("providerOrderId").asText();
        withGuestToken(MockMvcRequestBuilders.post("/api/guest/orders/" + firstId + "/payment/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerOrderId\":\"" + providerOrderId + "\",\"providerPaymentId\":\"pay_1\"}"), firstToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        withGuestToken(MockMvcRequestBuilders.post("/api/guest/orders/" + firstId + "/cancel"), firstToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("REFUND_PENDING"));
        withGuestToken(MockMvcRequestBuilders.get("/api/guest/orders/" + firstId), firstToken)
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        withGuestToken(MockMvcRequestBuilders.post("/api/guest/orders/" + firstId + "/payment"), null).andExpect(status().isUnauthorized());
    }

    @Test
    void staffPhonesCannotCheckOutAsGuests() throws Exception {
        String ownerPhone = randomPhone();
        client.register(ownerPhone, "RESTAURANT_OWNER").andExpect(status().isCreated());

        guestOrder("Owner", ownerPhone, "PAY_LATER").andExpect(status().isConflict());
    }

    @Test
    void guestCheckoutNeedsAValidTableSession() throws Exception {
        Map<String, Object> body = guestBody("Meera", randomPhone(), "PAY_LATER");
        body.put("tableSessionToken", "garbage");
        client.postJson("/api/guest/orders", body).andExpect(status().isBadRequest());
        body.remove("tableSessionToken");
        client.postJson("/api/guest/orders", body).andExpect(status().isBadRequest());
    }

    @Test
    void failedGuestOrderDoesNotCreateAUser() throws Exception {
        String phone = randomPhone();
        Map<String, Object> body = guestBody("Meera", phone, "PAY_LATER");
        body.put("items", List.of(Map.of("menuItemId", "00000000-0000-0000-0000-000000000000", "quantity", 1)));

        client.postJson("/api/guest/orders", body).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from users where phone = ?", Integer.class, phone)).isZero();
    }

    @Test
    void guestCanLaterRegisterAndSeeTheirOrders() throws Exception {
        String phone = randomPhone();
        String orderId = client.body(guestOrder("Meera", phone, "PAY_LATER")).path("order").path("id").asText();
        client.login(phone, "anything-at-all").andExpect(status().isUnauthorized());

        String token = client.body(client.register(phone, null).andExpect(status().isCreated())).path("accessToken").asText();
        client.send(GET, "/api/orders", token, null).andExpect(jsonPath("$.items[0].id").value(orderId));
        // Claimed accounts can't be claimed again.
        client.register(phone, null).andExpect(status().isConflict());
    }

    @Test
    void simultaneousFirstOrdersForANewPhoneCreateOneUser() throws Exception {
        String phone = randomPhone();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(json.writeValueAsString(guestBody("Meera", phone, "PAY_LATER")), headers);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return rest.postForEntity("/api/guest/orders", request, String.class);
                }));
            }
            start.countDown();
            for (var f : futures) {
                statuses.add(f.get().getStatusCode().value());
            }
        }
        assertThat(statuses).containsOnly(201);
        assertThat(jdbc.queryForObject("select count(*) from users where phone = ?", Integer.class, phone)).isEqualTo(1);
    }

    private ResultActions guestOrder(String name, String phone, String paymentMethod) throws Exception {
        return client.postJson("/api/guest/orders", guestBody(name, phone, paymentMethod));
    }

    private Map<String, Object> guestBody(String name, String phone, String paymentMethod) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("phone", phone);
        body.put("tableSessionToken", session);
        body.put("paymentMethod", paymentMethod);
        body.put("items", List.of(Map.of("menuItemId", itemId, "quantity", 2)));
        return body;
    }

    private ResultActions withGuestToken(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                         String token) throws Exception {
        if (token != null) {
            request.header("X-Guest-Order-Token", token);
        }
        return mvc.perform(request);
    }
}
