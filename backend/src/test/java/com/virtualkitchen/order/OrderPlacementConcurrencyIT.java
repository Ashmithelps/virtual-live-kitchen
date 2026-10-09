package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP requests on the embedded server, fired at the same instant for one restaurant. */
class OrderPlacementConcurrencyIT extends IntegrationTest {

    private static final int REQUESTS = 25;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @Test
    void parallelOrdersGetDistinctConsecutiveDisplayNumbers() throws Exception {
        OrderTestKitchen k = new OrderTestKitchen(new AuthClient(mvc, json));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(k.customer);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(json.writeValueAsString(k.order("TAKEAWAY")), headers);
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> numbers = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return rest.postForEntity("/api/orders", request, String.class);
                }));
            }
            start.countDown();
            for (Future<ResponseEntity<String>> f : futures) {
                ResponseEntity<String> response = f.get();
                assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(201);
                JsonNode body = json.readTree(response.getBody());
                numbers.add(body.path("displayNumber").asInt());
            }
        }

        assertThat(numbers).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, REQUESTS).boxed().toList());
        assertThat(jdbc.queryForObject("select count(distinct display_number) from orders where restaurant_id = ?::uuid",
                Integer.class, k.restaurantId)).isEqualTo(REQUESTS);
    }
}
