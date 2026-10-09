package com.virtualkitchen.kitchen;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitchenStationRepository extends JpaRepository<KitchenStation, UUID> {

    List<KitchenStation> findByRestaurantIdOrderByNameAsc(UUID restaurantId);

    Optional<KitchenStation> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);
}
