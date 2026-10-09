package com.virtualkitchen.order.dto;

import com.virtualkitchen.order.OrderStatus;
import com.virtualkitchen.order.PaymentMethod;
import com.virtualkitchen.order.PaymentStatus;
import com.virtualkitchen.restaurant.OrderType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        int displayNumber,
        LocalDate businessDate,
        UUID restaurantId,
        String restaurantName,
        OrderType orderType,
        String tableLabel,
        String deliveryAddress,
        Double deliveryDistanceKm,
        OrderStatus status,
        String statusReason,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        PaymentInfo payment,
        List<OrderItemResponse> items,
        long subtotalPaise,
        long taxPaise,
        long totalPaise,
        String notes,
        Preparation preparation,
        OrderTimeline timeline) {

    /** Who cooked it and where; set when the order moved to PREPARING. */
    public record Preparation(UUID chefId, String chefDisplayName, UUID stationId, String stationName) {
    }
}
