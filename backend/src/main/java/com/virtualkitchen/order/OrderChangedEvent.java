package com.virtualkitchen.order;

import com.virtualkitchen.order.dto.OrderResponse;

/**
 * Raised inside the transaction whenever an order changes in a way someone watching it should
 * see (placed, paid, status change, refund). Delivered to listeners after commit.
 */
public record OrderChangedEvent(OrderResponse order, boolean visibleToRestaurant) {
}
