package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Owner settles pay-at-table bills in person (cash / UPI / card). */
class CounterPaymentIT extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    AuthClient client;
    OrderTestKitchen k;
    String base;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        k = new OrderTestKitchen(client);
        base = "/api/owner/restaurants/" + k.restaurantId + "/orders";
    }

    @Test
    void servedPayAtTableBillIsListedAndSettledAtTheCounter() throws Exception {
        String id = payLaterOrder();
        k.advance(id, "ACCEPTED", "PREPARING", "READY", "SERVED");
        client.send(GET, base + "/unpaid", k.owner, null).andExpect(jsonPath("$[*].id", hasItem(id)));

        client.send(POST, base + "/" + id + "/payment/counter", k.owner, Map.of("method", "UPI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.payment.provider").value("counter"))
                .andExpect(jsonPath("$.payment.counterMethod").value("UPI"))
                .andExpect(jsonPath("$.payment.amountPaise").value(62685));

        client.send(GET, base + "/unpaid", k.owner, null).andExpect(jsonPath("$[*].id", not(hasItem(id))));
        Map<String, Object> row = jdbc.queryForMap("select provider, status, counter_method, amount_paise, recorded_by from payments where order_id = ?::uuid", id);
        assertThat(row.get("status")).isEqualTo("CAPTURED");
        assertThat(row.get("amount_paise")).isEqualTo(62685L);
        assertThat(row.get("recorded_by")).isNotNull();

        // Settled: no second counter payment, no online payment either.
        client.send(POST, base + "/" + id + "/payment/counter", k.owner, Map.of("method", "CASH")).andExpect(status().isConflict());
        k.startPayment(id).andExpect(status().isConflict());
    }

    @Test
    void onlyOpenPayAtTableBillsCanBeSettledByTheirOwner() throws Exception {
        String prepaid = k.visibleOrder("TAKEAWAY");
        client.send(POST, base + "/" + prepaid + "/payment/counter", k.owner, Map.of("method", "CASH")).andExpect(status().isConflict());

        String payLater = payLaterOrder();
        OrderTestKitchen other = new OrderTestKitchen(client);
        client.send(POST, "/api/owner/restaurants/" + other.restaurantId + "/orders/" + payLater + "/payment/counter", other.owner,
                Map.of("method", "CASH")).andExpect(status().isNotFound());
        client.send(POST, base + "/" + payLater + "/payment/counter", k.customer, Map.of("method", "CASH")).andExpect(status().isForbidden());
        client.send(POST, base + "/" + payLater + "/payment/counter", k.owner, Map.of()).andExpect(status().isBadRequest());

        k.ownerSets(payLater, "REJECTED").andExpect(status().isOk());
        client.send(POST, base + "/" + payLater + "/payment/counter", k.owner, Map.of("method", "CASH")).andExpect(status().isConflict());
    }

    @Test
    void payAtTableCustomerCanPayOnlineInstead() throws Exception {
        String id = payLaterOrder();
        k.pay(id, "pay_from_tracking_page").andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        client.send(GET, base + "/unpaid", k.owner, null).andExpect(jsonPath("$[*].id", not(hasItem(id))));
    }

    private String payLaterOrder() throws Exception {
        Map<String, Object> body = k.order("DINE_IN");
        body.put("paymentMethod", "PAY_LATER");
        return k.placed(body).path("id").asText();
    }
}
