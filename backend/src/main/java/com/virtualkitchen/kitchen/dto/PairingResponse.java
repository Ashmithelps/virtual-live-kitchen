package com.virtualkitchen.kitchen.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Show {@code qrSvgDataUri} (an image) for the station phone to scan; it opens
 * {@code pairingUrl} = {WEB_BASE_URL}/station#pair=<one-time code>.
 */
public record PairingResponse(UUID stationId, String stationName, String pairingUrl, String qrSvgDataUri, Instant expiresAt) {

    @Override
    public String toString() {
        return "PairingResponse[stationId=" + stationId + ", expiresAt=" + expiresAt + "]";
    }
}
