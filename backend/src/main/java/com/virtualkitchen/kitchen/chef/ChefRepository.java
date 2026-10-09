package com.virtualkitchen.kitchen.chef;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChefRepository extends JpaRepository<Chef, UUID> {

    Optional<Chef> findByUserIdAndRemovedAtIsNull(UUID userId);

    Optional<Chef> findByIdAndRestaurantIdAndRemovedAtIsNull(UUID id, UUID restaurantId);

    List<Chef> findByRestaurantIdAndRemovedAtIsNullOrderByDisplayNameAsc(UUID restaurantId);

    /** Serialises consent changes against station assignment for one chef. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Chef c where c.id = :id")
    Optional<Chef> lockById(@Param("id") UUID id);
}
