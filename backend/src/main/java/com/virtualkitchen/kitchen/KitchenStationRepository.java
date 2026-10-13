package com.virtualkitchen.kitchen;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitchenStationRepository extends JpaRepository<KitchenStation, UUID> {

    List<KitchenStation> findByRestaurantIdOrderByNameAsc(UUID restaurantId);

    Optional<KitchenStation> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);

    Optional<KitchenStation> findByDeviceTokenHash(String deviceTokenHash);

    Optional<KitchenStation> findByPairingCodeHash(String pairingCodeHash);

    /** Heartbeat; writes at most once per {@code staleBefore} window so device polling doesn't hammer the row. */
    @Modifying
    @Query(value = """
            UPDATE kitchen_stations SET device_last_seen_at = :now
            WHERE id = :id AND (device_last_seen_at IS NULL OR device_last_seen_at < :staleBefore)
            """, nativeQuery = true)
    int touchDevice(@Param("id") UUID id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
