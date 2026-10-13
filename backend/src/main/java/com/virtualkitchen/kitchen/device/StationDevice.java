package com.virtualkitchen.kitchen.device;

import java.util.UUID;

/** Principal for a paired station phone. It is not a user: it can only reach device endpoints. */
public record StationDevice(UUID stationId, UUID restaurantId) {

    public static final String AUTHORITY = "ROLE_STATION_DEVICE";
}
