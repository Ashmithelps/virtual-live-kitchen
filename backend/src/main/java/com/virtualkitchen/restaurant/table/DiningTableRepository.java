package com.virtualkitchen.restaurant.table;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningTableRepository extends JpaRepository<DiningTable, UUID> {

    List<DiningTable> findByRestaurantIdOrderByLabelAsc(UUID restaurantId);

    Optional<DiningTable> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Optional<DiningTable> findByQrToken(String qrToken);

    boolean existsByRestaurantIdAndLabelIgnoreCase(UUID restaurantId, String label);
}
