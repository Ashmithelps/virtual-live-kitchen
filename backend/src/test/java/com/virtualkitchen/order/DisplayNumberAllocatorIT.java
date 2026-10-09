package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import com.virtualkitchen.support.OwnerFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayNumberAllocatorIT extends IntegrationTest {

    private static final int THREADS = 50;

    @Autowired DisplayNumberAllocator allocator;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void parallelAllocationsAreUniqueAndGapless() throws Exception {
        UUID restaurant = newRestaurant();
        LocalDate day = LocalDate.of(2026, 10, 10);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> numbers = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return tx.execute(status -> allocator.next(restaurant, day));
                }));
            }
            start.countDown();
            for (Future<Integer> f : futures) {
                numbers.add(f.get());
            }
        }

        assertThat(numbers).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, THREADS).boxed().toList());
    }

    @Test
    void numbersResetPerDayAndAreIndependentPerRestaurant() throws Exception {
        UUID a = newRestaurant();
        UUID b = newRestaurant();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        LocalDate today = LocalDate.of(2026, 10, 10);

        assertThat(next(tx, a, today)).isEqualTo(1);
        assertThat(next(tx, a, today)).isEqualTo(2);
        assertThat(next(tx, b, today)).isEqualTo(1);
        assertThat(next(tx, a, today.plusDays(1))).isEqualTo(1);
    }

    @Test
    void rolledBackAllocationIsReused() throws Exception {
        UUID restaurant = newRestaurant();
        LocalDate day = LocalDate.of(2026, 10, 10);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.execute(status -> {
            allocator.next(restaurant, day);
            status.setRollbackOnly();
            return null;
        });

        assertThat(next(tx, restaurant, day)).isEqualTo(1);
    }

    private int next(TransactionTemplate tx, UUID restaurant, LocalDate day) {
        Integer number = tx.execute(status -> allocator.next(restaurant, day));
        return number == null ? -1 : number;
    }

    private UUID newRestaurant() throws Exception {
        AuthClient client = new AuthClient(mvc, json);
        String owner = client.approvedOwnerToken();
        return UUID.fromString(new OwnerFixtures(client).createRestaurant(owner).path("id").asText());
    }
}
