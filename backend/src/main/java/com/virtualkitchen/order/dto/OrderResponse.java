package com.virtualkitchen.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.virtualkitchen.order.ClipStatus;
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
        @Schema(nullable = true) String tableLabel,
        @Schema(nullable = true) String deliveryAddress,
        @Schema(nullable = true) Double deliveryDistanceKm,
        OrderStatus status,
        @Schema(nullable = true) String statusReason,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        @Schema(nullable = true) PaymentSummary payment,
        List<OrderItemResponse> items,
        long subtotalPaise,
        long taxPaise,
        long totalPaise,
        @Schema(nullable = true) String notes,
        @Schema(nullable = true) Preparation preparation,
        @Schema(nullable = true) ClipStatus clipStatus,
        @Schema(nullable = true) ClipView clip,
        OrderTimeline timeline) {

    /** Who cooked it and where; set when the order moved to PREPARING. */
    public record Preparation(UUID chefId, @Schema(nullable = true) String chefDisplayName, UUID stationId, @Schema(nullable = true) String stationName) {
    }
}
