package com.virtualkitchen.kitchen;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StationAssignmentRepository extends JpaRepository<StationAssignment, StationAssignment.Key> {

    List<StationAssignment> findByRestaurantId(UUID restaurantId);

    @Query("select a from StationAssignment a where a.id.chefId = :chefId")
    List<StationAssignment> findByChefId(@Param("chefId") UUID chefId);

    @Modifying
    @Query("delete from StationAssignment a where a.id.chefId = :chefId")
    int deleteByChefId(@Param("chefId") UUID chefId);
}
