package com.virtualkitchen.order.guest;

import com.virtualkitchen.order.dto.OrderResponse;

import java.time.Instant;

/**
 * {@code guestToken} grants access to this one order only (view, pay, cancel) via the
 * {@code X-Guest-Order-Token} header. It is not a login.
 */
public record GuestOrderResponse(OrderResponse order, String guestToken, Instant guestTokenExpiresAt) {

    @Override
    public String toString() {
        return "GuestOrderResponse[order=" + order.id() + ", guestToken=***]";
    }
}
