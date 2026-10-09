package com.virtualkitchen.order;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.restaurant.OrderType;
import org.springframework.http.HttpStatus;

public class IllegalOrderTransitionException extends ApiException {

    public IllegalOrderTransitionException(OrderType type, OrderStatus from, OrderStatus to) {
        super(HttpStatus.CONFLICT, "Cannot move a " + type + " order from " + from + " to " + to);
    }
}
