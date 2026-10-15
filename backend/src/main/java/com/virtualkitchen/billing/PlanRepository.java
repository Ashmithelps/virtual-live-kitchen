package com.virtualkitchen.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findAllByOrderBySortOrderAscPricePaiseAsc();

    List<Plan> findByActiveTrueOrderBySortOrderAscPricePaiseAsc();

    Optional<Plan> findByCode(String code);
}
