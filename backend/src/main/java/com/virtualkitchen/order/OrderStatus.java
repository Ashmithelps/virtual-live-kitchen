package com.virtualkitchen.order;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PLACED,
    ACCEPTED,
    REJECTED,
    PREPARING,
    READY,
    SERVED,
    PICKED_UP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    public static final Set<OrderStatus> TERMINAL = EnumSet.of(REJECTED, SERVED, PICKED_UP, DELIVERED, CANCELLED);
    public static final Set<OrderStatus> ACTIVE = EnumSet.complementOf(EnumSet.copyOf(TERMINAL));

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
