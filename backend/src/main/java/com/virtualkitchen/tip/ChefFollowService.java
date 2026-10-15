package com.virtualkitchen.tip;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.tip.dto.FollowedChefResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Customers following chefs (so the chef can be tipped at checkout, and later: notifications). */
@Service
public class ChefFollowService {

    private final JdbcTemplate jdbc;
    private final ObjectStorage storage;
    private final Clock clock;

    public ChefFollowService(JdbcTemplate jdbc, ObjectStorage storage, Clock clock) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.clock = clock;
    }

    /** Only active chefs of live restaurants can be followed. */
    @Transactional
    public void follow(UUID customerId, UUID chefId) {
        Boolean followable = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM chefs c JOIN restaurants r ON r.id = c.restaurant_id
                               WHERE c.id = ? AND c.removed_at IS NULL AND r.status = 'ACTIVE')
                """, Boolean.class, chefId);
        if (!Boolean.TRUE.equals(followable)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Chef not found");
        }
        jdbc.update("INSERT INTO chef_follows (customer_id, chef_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                customerId, chefId, Timestamp.from(clock.instant()));
    }

    @Transactional
    public void unfollow(UUID customerId, UUID chefId) {
        jdbc.update("DELETE FROM chef_follows WHERE customer_id = ? AND chef_id = ?", customerId, chefId);
    }

    @Transactional(readOnly = true)
    public boolean follows(UUID customerId, UUID chefId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM chef_follows WHERE customer_id = ? AND chef_id = ?)",
                Boolean.class, customerId, chefId));
    }

    /** The customer's followed chefs (optionally at one restaurant), active ones only. */
    @Transactional(readOnly = true)
    public List<FollowedChefResponse> followed(UUID customerId, UUID restaurantId) {
        return jdbc.query("""
                SELECT c.id, c.display_name, c.photo_key, c.specialty, r.id AS restaurant_id, r.name, r.slug,
                       (p.status = 'ACTIVE') AS tippable
                FROM chef_follows f
                JOIN chefs c ON c.id = f.chef_id AND c.removed_at IS NULL
                JOIN restaurants r ON r.id = c.restaurant_id AND r.status = 'ACTIVE'
                LEFT JOIN chef_payout_accounts p ON p.chef_id = c.id
                WHERE f.customer_id = ? AND (CAST(? AS uuid) IS NULL OR r.id = ?)
                ORDER BY f.created_at DESC
                """, (rs, i) -> new FollowedChefResponse(rs.getObject("id", UUID.class), rs.getString("display_name"),
                storage.downloadUrl(rs.getString("photo_key")), rs.getString("specialty"), rs.getObject("restaurant_id", UUID.class),
                rs.getString("name"), rs.getString("slug"), rs.getBoolean("tippable")), customerId, restaurantId, restaurantId);
    }
}
