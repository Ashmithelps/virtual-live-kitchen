package com.virtualkitchen.order;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.restaurant.OrderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.virtualkitchen.order.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    /** Written out independently of the production table, straight from the spec. */
    private static final Set<String> LEGAL = Set.of(
            "*:PLACED>ACCEPTED", "*:PLACED>REJECTED", "*:PLACED>CANCELLED",
            "*:ACCEPTED>PREPARING", "*:ACCEPTED>CANCELLED",
            "*:PREPARING>READY",
            "DINE_IN:READY>SERVED",
            "TAKEAWAY:READY>PICKED_UP",
            "DELIVERY:READY>OUT_FOR_DELIVERY", "DELIVERY:OUT_FOR_DELIVERY>DELIVERED");

    private static boolean legal(OrderType type, OrderStatus from, OrderStatus to) {
        return LEGAL.contains("*:" + from + ">" + to) || LEGAL.contains(type + ":" + from + ">" + to);
    }

    static Stream<Arguments> illegalTransitions() {
        List<Arguments> cases = new ArrayList<>();
        for (OrderType type : OrderType.values())
            for (OrderStatus from : OrderStatus.values())
                for (OrderStatus to : OrderStatus.values())
                    if (!legal(type, from, to)) cases.add(Arguments.of(type, from, to));
        return cases.stream();
    }

    static Stream<Arguments> legalTransitions() {
        List<Arguments> cases = new ArrayList<>();
        for (OrderType type : OrderType.values())
            for (OrderStatus from : OrderStatus.values())
                for (OrderStatus to : OrderStatus.values())
                    if (legal(type, from, to)) cases.add(Arguments.of(type, from, to));
        return cases.stream();
    }

    @Test
    void enumeratesEveryCombination() {
        // 3 types × 10 × 10 = 300 pairs; 7 legal for dine-in and takeaway, 8 for delivery.
        assertThat(illegalTransitions().count()).isEqualTo(300 - 22);
        assertThat(legalTransitions().count()).isEqualTo(22);
    }

    @ParameterizedTest(name = "{0}: {1} -> {2} is rejected with 409 for every actor")
    @MethodSource("illegalTransitions")
    void everyIllegalTransitionIs409(OrderType type, OrderStatus from, OrderStatus to) {
        for (OrderActor actor : OrderActor.values()) {
            assertThatThrownBy(() -> OrderStateMachine.check(type, from, to, actor))
                    .isInstanceOf(IllegalOrderTransitionException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }
    }

    @ParameterizedTest(name = "{0}: {1} -> {2} is allowed for the owner")
    @MethodSource("legalTransitions")
    void everyLegalTransitionIsAllowedForOwner(OrderType type, OrderStatus from, OrderStatus to) {
        assertThatCode(() -> OrderStateMachine.check(type, from, to, OrderActor.OWNER)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}: {1} -> {2} actor rules")
    @MethodSource("legalTransitions")
    void chefsOnlyPrepareAndCustomersOnlyCancel(OrderType type, OrderStatus from, OrderStatus to) {
        assertActor(OrderActor.CHEF, type, from, to, to == PREPARING || to == READY);
        assertActor(OrderActor.CUSTOMER, type, from, to, to == CANCELLED);
    }

    @Test
    void transitionSetsStatusAndItsTimestamp() {
        Order order = newOrder(OrderType.DELIVERY);
        Instant t = Instant.parse("2026-10-10T06:00:00Z");

        OrderStateMachine.transition(order, ACCEPTED, OrderActor.OWNER, t.plusSeconds(1));
        OrderStateMachine.transition(order, PREPARING, OrderActor.CHEF, t.plusSeconds(2));
        OrderStateMachine.transition(order, READY, OrderActor.CHEF, t.plusSeconds(3));
        OrderStateMachine.transition(order, OUT_FOR_DELIVERY, OrderActor.OWNER, t.plusSeconds(4));
        OrderStateMachine.transition(order, DELIVERED, OrderActor.OWNER, t.plusSeconds(5));

        assertThat(order.getStatus()).isEqualTo(DELIVERED);
        assertThat(order.getAcceptedAt()).isEqualTo(t.plusSeconds(1));
        assertThat(order.getPreparingAt()).isEqualTo(t.plusSeconds(2));
        assertThat(order.getReadyAt()).isEqualTo(t.plusSeconds(3));
        assertThat(order.getOutForDeliveryAt()).isEqualTo(t.plusSeconds(4));
        assertThat(order.getDeliveredAt()).isEqualTo(t.plusSeconds(5));
    }

    @Test
    void failedTransitionLeavesOrderUntouched() {
        Order order = newOrder(OrderType.TAKEAWAY);

        assertThatThrownBy(() -> OrderStateMachine.transition(order, READY, OrderActor.OWNER, Instant.now()))
                .isInstanceOf(IllegalOrderTransitionException.class)
                .hasMessage("Cannot move a TAKEAWAY order from PLACED to READY");
        assertThat(order.getStatus()).isEqualTo(PLACED);
        assertThat(order.getReadyAt()).isNull();
    }

    private static void assertActor(OrderActor actor, OrderType type, OrderStatus from, OrderStatus to, boolean allowed) {
        if (allowed) {
            assertThatCode(() -> OrderStateMachine.check(type, from, to, actor)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> OrderStateMachine.check(type, from, to, actor))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    private static Order newOrder(OrderType type) {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), type, PaymentMethod.ONLINE, null,
                Instant.parse("2026-10-10T06:00:00Z"));
    }
}
