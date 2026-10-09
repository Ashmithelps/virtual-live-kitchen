package com.virtualkitchen.kitchen.dto;

import java.util.UUID;

/** Shown exactly once; only a hash is stored. Issuing a new token invalidates the previous one. */
public record DeviceTokenResponse(UUID stationId, String deviceToken) {

    @Override
    public String toString() {
        return "DeviceTokenResponse[stationId=" + stationId + ", deviceToken=***]";
    }
}
