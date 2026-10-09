package com.virtualkitchen.order;

import com.virtualkitchen.kitchen.KitchenStation;
import com.virtualkitchen.kitchen.KitchenStationRepository;
import com.virtualkitchen.kitchen.chef.Chef;
import com.virtualkitchen.kitchen.chef.ChefRepository;
import com.virtualkitchen.order.dto.OrderItemResponse;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.OrderTimeline;
import com.virtualkitchen.order.dto.PaymentInfo;
import com.virtualkitchen.payment.PaymentProvider;
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

    OrderMapper(RestaurantRepository restaurants, ChefRepository chefs, KitchenStationRepository stations) {
        this.restaurants = restaurants;
        this.chefs = chefs;
        this.stations = stations;
    }

    OrderResponse toResponse(Order order) {
        return toResponses(List.of(order)).get(0);
    }

    OrderResponse toResponse(Order order, PaymentProvider.PaymentSession session) {
        OrderResponse r = toResponse(order);
        PaymentInfo payment = new PaymentInfo(order.getPaymentProvider(), order.getPaymentProviderOrderId(),
                order.getTotalPaise(), session.clientData());
        return new OrderResponse(r.id(), r.displayNumber(), r.businessDate(), r.restaurantId(), r.restaurantName(),
                r.orderType(), r.tableLabel(), r.deliveryAddress(), r.deliveryDistanceKm(), r.status(), r.statusReason(),
                r.paymentMethod(), r.paymentStatus(), payment, r.items(), r.subtotalPaise(), r.taxPaise(),
                r.totalPaise(), r.notes(), r.preparation(), r.timeline());
    }

    List<OrderResponse> toResponses(List<Order> orders) {
        Map<UUID, Restaurant> restaurantById = byId(restaurants.findAllById(ids(orders, Order::getRestaurantId)), Restaurant::getId);
        Map<UUID, Chef> chefById = byId(chefs.findAllById(ids(orders, Order::getPreparedByChefId)), Chef::getId);
        Map<UUID, KitchenStation> stationById = byId(stations.findAllById(ids(orders, Order::getStationId)), KitchenStation::getId);
        return orders.stream().map(o -> map(o, restaurantById.get(o.getRestaurantId()),
                chefById.get(o.getPreparedByChefId()), stationById.get(o.getStationId()))).toList();
    }

    private OrderResponse map(Order o, Restaurant restaurant, Chef chef, KitchenStation station) {
        PaymentInfo payment = o.getPaymentStatus() == PaymentStatus.PENDING || o.getPaymentStatus() == PaymentStatus.FAILED
                ? new PaymentInfo(o.getPaymentProvider(), o.getPaymentProviderOrderId(), o.getTotalPaise(), null)
                : null;
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
