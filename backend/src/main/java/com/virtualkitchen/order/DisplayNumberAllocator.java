package com.virtualkitchen.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Hands out short order numbers (1, 2, 3 …) per restaurant per business day.
 *
 * <p>The upsert takes a row lock on the counter that is held until the caller's transaction ends,
 * so concurrent orders for the same restaurant and day get consecutive, never-duplicated numbers.
 * If the order insert rolls back, the counter increment rolls back too. Callers should allocate as
 * the last step before inserting, to keep the lock short. A unique constraint on
 * (restaurant_id, business_date, display_number) backs this up.
 */
@Component
public class DisplayNumberAllocator {

    private static final String NEXT_NUMBER_SQL = """
            INSERT INTO order_daily_counters (restaurant_id, business_date, last_number)
            VALUES (?, ?, 1)
            ON CONFLICT (restaurant_id, business_date)
            DO UPDATE SET last_number = order_daily_counters.last_number + 1
            RETURNING last_number
            """;

    private final JdbcTemplate jdbc;

    public DisplayNumberAllocator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int next(UUID restaurantId, LocalDate businessDate) {
        Integer number = jdbc.queryForObject(NEXT_NUMBER_SQL, Integer.class, restaurantId, businessDate);
        if (number == null) {
            throw new IllegalStateException("Counter upsert returned no row");
        }
        return number;
    }
}
