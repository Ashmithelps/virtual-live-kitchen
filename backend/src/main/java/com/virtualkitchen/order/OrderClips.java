package com.virtualkitchen.order;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Order-module API for the clip module: set an order's clip status and let watchers know. */
@Component
public class OrderClips {

    private final OrderRepository orders;
    private final OrderEvents events;

    OrderClips(OrderRepository orders, OrderEvents events) {
        this.orders = orders;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setClipStatus(UUID orderId, ClipStatus status, boolean notify) {
        Order order = orders.findById(orderId).orElseThrow();
        order.setClipStatus(status);
        if (notify) {
            events.changed(order);
        }
    }
}
