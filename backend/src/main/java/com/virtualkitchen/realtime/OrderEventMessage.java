package com.virtualkitchen.realtime;

import com.virtualkitchen.order.dto.OrderResponse;

/** Body of every message on the order topics. {@code type} leaves room for other events later. */
public record OrderEventMessage(String type, OrderResponse order) {

    public static OrderEventMessage updated(OrderResponse order) {
        return new OrderEventMessage("ORDER_UPDATED", order);
    }
}
