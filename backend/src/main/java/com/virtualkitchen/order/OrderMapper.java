package com.virtualkitchen.order;

import com.virtualkitchen.kitchen.KitchenStation;
import com.virtualkitchen.kitchen.KitchenStationRepository;
import com.virtualkitchen.kitchen.chef.Chef;
import com.virtualkitchen.kitchen.chef.ChefRepository;
import com.virtualkitchen.order.dto.OrderItemResponse;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.OrderTimeline;
import com.virtualkitchen.order.dto.PaymentSummary;
import com.virtualkitchen.payment.Payment;
import com.virtualkitchen.payment.PaymentRepository;
import com.virtualkitchen.restaurant.Restaurant;
import com.virtualkitchen.restaurant.RestaurantRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds responses; looks up restaurant/chef/station names in batches. Call inside a transaction. */
@Component
class OrderMapper {

    private final RestaurantRepository restaurants;
    private final ChefRepository chefs;
    private final KitchenStationRepository stations;
    private final PaymentRepository payments;

    OrderMapper(RestaurantRepository restaurants, ChefRepository chefs, KitchenStationRepository stations,
                PaymentRepository payments) {
        this.restaurants = restaurants;
        this.chefs = chefs;
        this.stations = stations;
        this.payments = payments;
    }

    OrderResponse toResponse(Order order) {
        return toResponses(List.of(order)).get(0);
    }

    List<OrderResponse> toResponses(List<Order> orders) {
        Map<UUID, Restaurant> restaurantById = byId(restaurants.findAllById(ids(orders, Order::getRestaurantId)), Restaurant::getId);
        Map<UUID, Chef> chefById = byId(chefs.findAllById(ids(orders, Order::getPreparedByChefId)), Chef::getId);
        Map<UUID, KitchenStation> stationById = byId(stations.findAllById(ids(orders, Order::getStationId)), KitchenStation::getId);
        Map<UUID, List<Payment>> paymentsByOrder = orders.isEmpty() ? Map.of()
                : payments.findByOrderIdInOrderByCreatedAtDesc(ids(orders, Order::getId)).stream()
                        .collect(Collectors.groupingBy(Payment::getOrderId));
        return orders.stream().map(o -> map(o, restaurantById.get(o.getRestaurantId()),
                chefById.get(o.getPreparedByChefId()), stationById.get(o.getStationId()),
                summary(paymentsByOrder.getOrDefault(o.getId(), List.of())))).toList();
    }

    /** The settled attempt if there is one, otherwise the most recent attempt. */
    private static PaymentSummary summary(List<Payment> attempts) {
        return attempts.stream().filter(Payment::isSettled).findFirst()
                .or(() -> attempts.stream().findFirst())
                .map(p -> new PaymentSummary(p.getProvider(), p.getStatus(), p.getAmountPaise(), p.getCounterMethod(), p.getFailureReason()))
                .orElse(null);
    }

    private OrderResponse map(Order o, Restaurant restaurant, Chef chef, KitchenStation station, PaymentSummary payment) {
        OrderResponse.Preparation preparation = o.getPreparedByChefId() == null ? null : new OrderResponse.Preparation(
                o.getPreparedByChefId(), chef == null ? null : chef.getDisplayName(),
                o.getStationId(), station == null ? null : station.getName());
        return new OrderResponse(o.getId(), o.getDisplayNumber(), o.getBusinessDate(), o.getRestaurantId(),
                restaurant == null ? null : restaurant.getName(), o.getOrderType(), o.getTableLabel(),
                o.getDeliveryAddress(), o.getDeliveryDistanceKm(), o.getStatus(), o.getStatusReason(),
                o.getPaymentMethod(), o.getPaymentStatus(), payment,
                o.getItems().stream().map(i -> new OrderItemResponse(i.getMenuItemId(), i.getItemName(),
                        i.getUnitPricePaise(), i.isVeg(), i.getQuantity(), i.getLineTotalPaise(), i.getNotes())).toList(),
                o.getSubtotalPaise(), o.getTaxPaise(), o.getTotalPaise(), o.getNotes(), preparation,
                new OrderTimeline(o.getPlacedAt(), o.getPaidAt(), o.getAcceptedAt(), o.getRejectedAt(),
                        o.getPreparingAt(), o.getReadyAt(), o.getServedAt(), o.getPickedUpAt(),
                        o.getOutForDeliveryAt(), o.getDeliveredAt(), o.getCancelledAt(), o.getRefundedAt()));
    }

    private static List<UUID> ids(Collection<Order> orders, Function<Order, UUID> getter) {
        return orders.stream().map(getter).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<UUID, T> byId(List<T> entities, Function<T, UUID> id) {
        return entities.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
