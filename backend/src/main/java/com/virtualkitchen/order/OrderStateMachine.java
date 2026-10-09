package com.virtualkitchen.order;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.restaurant.OrderType;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.virtualkitchen.order.OrderStatus.ACCEPTED;
import static com.virtualkitchen.order.OrderStatus.CANCELLED;
import static com.virtualkitchen.order.OrderStatus.DELIVERED;
import static com.virtualkitchen.order.OrderStatus.OUT_FOR_DELIVERY;
import static com.virtualkitchen.order.OrderStatus.PICKED_UP;
import static com.virtualkitchen.order.OrderStatus.PLACED;
import static com.virtualkitchen.order.OrderStatus.PREPARING;
import static com.virtualkitchen.order.OrderStatus.READY;
import static com.virtualkitchen.order.OrderStatus.REJECTED;
import static com.virtualkitchen.order.OrderStatus.SERVED;

/**
 * The single place order status changes are decided and applied.
 *
 * <pre>
 * PLACED   -> ACCEPTED | REJECTED | CANCELLED
 * ACCEPTED -> PREPARING | CANCELLED
 * PREPARING -> READY
 * READY    -> SERVED (dine-in) | PICKED_UP (takeaway) | OUT_FOR_DELIVERY (delivery)
 * OUT_FOR_DELIVERY -> DELIVERED (delivery)
 * </pre>
 *
 * Anything else is a 409. Of the legal moves, customers may only cancel and chefs may only move
 * ACCEPTED -> PREPARING -> READY (403 otherwise); owners may do all of them.
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> COMMON = Map.of(
            PLACED, EnumSet.of(ACCEPTED, REJECTED, CANCELLED),
            ACCEPTED, EnumSet.of(PREPARING, CANCELLED),
            PREPARING, EnumSet.of(READY));

    private static final Map<OrderActor, Set<OrderStatus>> ACTOR_TARGETS = Map.of(
            OrderActor.CUSTOMER, EnumSet.of(CANCELLED),
            OrderActor.CHEF, EnumSet.of(PREPARING, READY),
            OrderActor.OWNER, EnumSet.allOf(OrderStatus.class));

    private OrderStateMachine() {
    }

    /** Legal next statuses for an order of {@code type} currently in {@code from}. */
    public static Set<OrderStatus> allowedTargets(OrderType type, OrderStatus from) {
        Set<OrderStatus> targets = EnumSet.noneOf(OrderStatus.class);
        targets.addAll(COMMON.getOrDefault(from, Set.of()));
        if (from == READY) {
            targets.add(switch (type) {
                case DINE_IN -> SERVED;
                case TAKEAWAY -> PICKED_UP;
                case DELIVERY -> OUT_FOR_DELIVERY;
            });
        }
        if (from == OUT_FOR_DELIVERY && type == OrderType.DELIVERY) {
            targets.add(DELIVERED);
        }
        return targets;
    }

    /**
     * @throws IllegalOrderTransitionException (409) if the move is not in the table above
     * @throws ApiException (403) if the move is legal but not permitted for {@code actor}
     */
    public static void check(OrderType type, OrderStatus from, OrderStatus to, OrderActor actor) {
        if (!allowedTargets(type, from).contains(to)) {
            throw new IllegalOrderTransitionException(type, from, to);
        }
        if (!ACTOR_TARGETS.get(actor).contains(to)) {
            throw new ApiException(HttpStatus.FORBIDDEN, actor + " may not move an order to " + to);
        }
    }

    /** Validates, then sets the new status and its timestamp. */
    public static void transition(Order order, OrderStatus to, OrderActor actor, Instant now) {
        check(order.getOrderType(), order.getStatus(), to, actor);
        order.applyStatus(to, now);
    }
}
