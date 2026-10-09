package com.virtualkitchen.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    Optional<Restaurant> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Restaurant> findBySlugAndStatus(String slug, RestaurantStatus status);

    boolean existsBySlug(String slug);

    List<Restaurant> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    List<Restaurant> findByStatusOrderByCreatedAtAsc(RestaurantStatus status);
}
