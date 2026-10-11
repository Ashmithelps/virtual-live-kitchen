package com.virtualkitchen.payment;

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

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<Payment> findByOrderIdInOrderByCreatedAtDesc(Collection<UUID> orderIds);

    /** Row lock: checkout confirmation and webhooks for the same payment are applied one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.provider = :provider and p.providerOrderId = :providerOrderId")
    Optional<Payment> lockByProviderOrderId(@Param("provider") String provider, @Param("providerOrderId") String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.provider = :provider and p.providerPaymentId = :providerPaymentId")
    List<Payment> lockByProviderPaymentId(@Param("provider") String provider, @Param("providerPaymentId") String providerPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> lockById(@Param("id") UUID id);

    List<Payment> findByStatusAndRefundRequestedAtBefore(PaymentAttemptStatus status, Instant before);
}
