package com.virtualkitchen.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantSubscriptionRepository extends JpaRepository<RestaurantSubscription, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RestaurantSubscription s where s.restaurantId = :id")
    Optional<RestaurantSubscription> lock(@Param("id") UUID restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RestaurantSubscription s where s.provider = :provider and s.providerSubscriptionId = :sid")
    Optional<RestaurantSubscription> lockByGateway(@Param("provider") String provider, @Param("sid") String subscriptionId);

    @Query("""
            select s.restaurantId from RestaurantSubscription s
            where (s.status = com.virtualkitchen.billing.SubscriptionStatus.TRIALING and s.paymentMethodReady = false and s.trialEndsAt < :now)
               or (s.status = com.virtualkitchen.billing.SubscriptionStatus.PAST_DUE and s.graceUntil < :now)
               or (s.status = com.virtualkitchen.billing.SubscriptionStatus.CANCELLED and (s.currentPeriodEnd is null or s.currentPeriodEnd < :now))
            """)
    List<UUID> needingEnforcement(@Param("now") Instant now);
}
