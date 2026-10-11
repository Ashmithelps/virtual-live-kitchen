package com.virtualkitchen.order.dto;

import com.virtualkitchen.order.PaymentMethod;
import com.virtualkitchen.restaurant.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * DINE_IN needs {@code tableSessionToken} (from GET /api/tables/{qrToken}), which pins the order to
 * the scanned table; DELIVERY needs
 * {@code deliveryAddress}; TAKEAWAY needs neither. {@code paymentMethod} defaults to ONLINE;
 * PAY_LATER is allowed for DINE_IN only.
 */
public record PlaceOrderRequest(
        @NotNull UUID restaurantId,
        @NotNull OrderType orderType,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid OrderLineRequest> items,
        @Size(max = 1000) String tableSessionToken,
        @Valid DeliveryAddressRequest deliveryAddress,
        PaymentMethod paymentMethod,
        @Size(max = 500) String notes) {
}
