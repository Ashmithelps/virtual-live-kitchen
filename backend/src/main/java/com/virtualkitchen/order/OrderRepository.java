package com.virtualkitchen.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByCustomerIdOrderByPlacedAtDesc(UUID customerId, Pageable pageable);

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<Order> findByIdAndRestaurantIdAndPaymentStatusIn(UUID id, UUID restaurantId,
                                                              Collection<PaymentStatus> paymentStatuses);

    List<Order> findByRestaurantIdAndStatusInAndPaymentStatusInOrderByPlacedAtAsc(
            UUID restaurantId, Collection<OrderStatus> statuses, Collection<PaymentStatus> paymentStatuses);
}
