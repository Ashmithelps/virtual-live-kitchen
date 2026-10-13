package com.virtualkitchen.realtime;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The only destinations clients may subscribe to. */
public final class Topics {

    private static final Pattern RESTAURANT_ORDERS = Pattern.compile("^/topic/restaurant/([0-9a-fA-F-]{36})/orders$");
    private static final Pattern ORDER = Pattern.compile("^/topic/order/([0-9a-fA-F-]{36})$");
    private static final Pattern STATION = Pattern.compile("^/topic/station/([0-9a-fA-F-]{36})$");

    private Topics() {
    }

    /** Owner and chefs of that restaurant: every order the restaurant can see. */
    public static String restaurantOrders(UUID restaurantId) {
        return "/topic/restaurant/" + restaurantId + "/orders";
    }

    /** That order's customer only. */
    public static String order(UUID orderId) {
        return "/topic/order/" + orderId;
    }

    /** That station's recording phone only: footage windows to upload. */
    public static String station(UUID stationId) {
        return "/topic/station/" + stationId;
    }

    static Optional<UUID> stationIdOf(String destination) {
        return match(STATION, destination);
    }

    static Optional<UUID> restaurantIdOf(String destination) {
        return match(RESTAURANT_ORDERS, destination);
    }

    static Optional<UUID> orderIdOf(String destination) {
        return match(ORDER, destination);
    }

    private static Optional<UUID> match(Pattern pattern, String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Matcher m = pattern.matcher(destination);
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(m.group(1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
