package com.virtualkitchen.clip;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClipRepository extends JpaRepository<Clip, UUID> {

    Optional<Clip> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Clip c where c.orderId = :orderId")
    Optional<Clip> lockByOrderId(@Param("orderId") UUID orderId);

    List<Clip> findByOrderIdIn(Collection<UUID> orderIds);

    List<Clip> findByStatusAndExpiresAtBefore(ClipState status, Instant before);
}
