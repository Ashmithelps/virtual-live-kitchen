package com.virtualkitchen.order;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.kitchen.KitchenStaffing;
import com.virtualkitchen.kitchen.chef.Chef;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.OrderStatusChangeRequest;
import com.virtualkitchen.restaurant.RestaurantAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Restaurant side (owners and chefs). Orders whose online payment hasn't completed are invisible
 * here: listing skips them and direct access answers 404.
 */
@Service
public class RestaurantOrderService {

    private final OrderRepository orders;
    private final RestaurantAccess restaurantAccess;
    private final KitchenStaffing kitchenStaffing;
    private final OrderPayments payments;
    private final OrderMapper mapper;
    private final Clock clock;

    public RestaurantOrderService(OrderRepository orders, RestaurantAccess restaurantAccess,
                                  KitchenStaffing kitchenStaffing, OrderPayments payments, OrderMapper mapper,
                                  Clock clock) {
        this.orders = orders;
        this.restaurantAccess = restaurantAccess;
        this.kitchenStaffing = kitchenStaffing;
        this.payments = payments;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ── owner ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OrderResponse> listActiveForOwner(UUID ownerId, UUID restaurantId, OrderStatus status) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return listActive(restaurantId, status);
    }

    @Transactional(readOnly = true)
    public OrderResponse getForOwner(UUID ownerId, UUID restaurantId, UUID orderId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return mapper.toResponse(requireVisible(restaurantId, orderId));
    }

    @Transactional
    public OrderResponse changeStatusAsOwner(UUID ownerId, UUID restaurantId, UUID orderId, OrderStatusChangeRequest request) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        Order order = requireVisible(restaurantId, orderId);
        return changeStatus(order, OrderActor.OWNER, request, request.chefId());
    }

    // ── chef ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OrderResponse> listActiveForChef(UUID chefUserId, OrderStatus status) {
        Chef chef = kitchenStaffing.requireChefByUser(chefUserId);
        return listActive(chef.getRestaurantId(), status);
    }

    @Transactional
    public OrderResponse changeStatusAsChef(UUID chefUserId, UUID orderId, OrderStatusChangeRequest request) {
        Chef chef = kitchenStaffing.requireChefByUser(chefUserId);
        Order order = requireVisible(chef.getRestaurantId(), orderId);
        if (request.chefId() != null && !request.chefId().equals(chef.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Chefs can only record themselves as preparing an order");
        }
        return changeStatus(order, OrderActor.CHEF, request, chef.getId());
    }

    // ── shared ───────────────────────────────────────────────────────────────

    private OrderResponse changeStatus(Order order, OrderActor actor, OrderStatusChangeRequest request, UUID chefId) {
        OrderStatus target = request.status();
        Instant now = clock.instant();
        // Validate the move first so an illegal transition is a 409 regardless of missing fields.
        OrderStateMachine.check(order.getOrderType(), order.getStatus(), target, actor);

        if (target == OrderStatus.PREPARING) {
            if (request.stationId() == null || chefId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PREPARING requires stationId"
                        + (actor == OrderActor.OWNER ? " and chefId" : ""));
            }
            kitchenStaffing.requireRecordableOnStation(order.getRestaurantId(), request.stationId(), chefId);
            order.assignPreparation(chefId, request.stationId());
        }
        OrderStateMachine.transition(order, target, actor, now);
        if (target == OrderStatus.REJECTED || target == OrderStatus.CANCELLED) {
            order.setStatusReason(request.reason() == null || request.reason().isBlank()
                    ? (target == OrderStatus.REJECTED ? "Rejected by restaurant" : "Cancelled by restaurant")
                    : request.reason().strip());
            payments.refundIfPaid(order);
        }
        orders.saveAndFlush(order); // surfaces @Version conflicts as 409 inside this request
        return mapper.toResponse(order);
    }

    private List<OrderResponse> listActive(UUID restaurantId, OrderStatus status) {
        Set<OrderStatus> statuses = status == null ? OrderStatus.ACTIVE : EnumSet.of(status);
        return mapper.toResponses(orders.findByRestaurantIdAndStatusInAndPaymentStatusInOrderByPlacedAtAsc(
                restaurantId, statuses, PaymentStatus.VISIBLE_TO_RESTAURANT));
    }

    private Order requireVisible(UUID restaurantId, UUID orderId) {
        return orders.findByIdAndRestaurantIdAndPaymentStatusIn(orderId, restaurantId, PaymentStatus.VISIBLE_TO_RESTAURANT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
