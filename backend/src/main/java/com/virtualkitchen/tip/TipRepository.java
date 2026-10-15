package com.virtualkitchen.tip;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TipRepository extends JpaRepository<Tip, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tip t where t.id = :id")
    Optional<Tip> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tip t where t.provider = :provider and t.providerOrderId = :providerOrderId")
    Optional<Tip> lockByProviderOrderId(@Param("provider") String provider, @Param("providerOrderId") String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tip t where t.provider = :provider and t.providerPaymentId = :paymentId")
    Optional<Tip> lockByProviderPaymentId(@Param("provider") String provider, @Param("paymentId") String paymentId);

    List<Tip> findByOrderIdAndCustomerIdOrderByCreatedAtDesc(UUID orderId, UUID customerId);

    Page<Tip> findByChefIdAndStatusInOrderByCreatedAtDesc(UUID chefId, Collection<TipStatus> statuses, Pageable pageable);
}
