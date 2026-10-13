package com.virtualkitchen.clip;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClipRequestRepository extends JpaRepository<ClipRequest, UUID> {

    Optional<ClipRequest> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClipRequest c where c.orderId = :orderId")
    Optional<ClipRequest> lockByOrderId(@Param("orderId") UUID orderId);

    List<ClipRequest> findByStationIdAndStatusOrderByRequestedAtAsc(UUID stationId, ClipRequestStatus status);

    List<ClipRequest> findByStatusAndRequestedAtBefore(ClipRequestStatus status, Instant before);

    /** Requests whose raw footage is still in storage and older than the cutoff. */
    @Query("select c from ClipRequest c where c.rawDeletedAt is null and c.requestedAt < :before")
    List<ClipRequest> findWithRawFootageOlderThan(@Param("before") Instant before);
}
