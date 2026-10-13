package com.virtualkitchen.kitchen.device;

import com.virtualkitchen.common.crypto.SecureTokens;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.kitchen.KitchenStation;
import com.virtualkitchen.kitchen.KitchenStationRepository;
import com.virtualkitchen.restaurant.RestaurantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Station phones: pairing by one-time code, and token authentication. */
@Service
public class StationDeviceService {

    private static final int DEVICE_TOKEN_BYTES = 32;
    private static final java.time.Duration LAST_SEEN_RESOLUTION = java.time.Duration.ofSeconds(15);

    private final KitchenStationRepository stations;
    private final RestaurantRepository restaurants;
    private final Clock clock;

    public StationDeviceService(KitchenStationRepository stations, RestaurantRepository restaurants, Clock clock) {
        this.stations = stations;
        this.restaurants = restaurants;
        this.clock = clock;
    }

    /** Also records that the phone is alive (for the owner's online indicator). */
    @Transactional
    public Optional<StationDevice> authenticate(String deviceToken) {
        Optional<StationDevice> device = stations.findByDeviceTokenHash(SecureTokens.sha256Hex(deviceToken))
                .map(s -> new StationDevice(s.getId(), s.getRestaurantId()));
        device.ifPresent(d -> {
            Instant now = clock.instant();
            stations.touchDevice(d.stationId(), now, now.minus(LAST_SEEN_RESOLUTION));
        });
        return device;
    }

    /**
     * Exchanges a pairing code (from the owner's QR) for a long-lived device token. The code works
     * once and for a limited time; pairing replaces any phone previously paired with the station.
     */
    @Transactional
    public PairedDevice pair(String pairingCode) {
        Instant now = clock.instant();
        KitchenStation station = stations.findByPairingCodeHash(SecureTokens.sha256Hex(pairingCode))
                .filter(s -> s.getPairingExpiresAt() != null && s.getPairingExpiresAt().isAfter(now))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "This pairing code is invalid or has expired. Show a new pairing QR on the kitchen display."));
        String token = SecureTokens.random(DEVICE_TOKEN_BYTES);
        station.completePairing(SecureTokens.sha256Hex(token), now);
        return new PairedDevice(token, describe(station));
    }

    @Transactional(readOnly = true)
    public StationInfo describe(StationDevice device) {
        return describe(stations.findById(device.stationId()).orElseThrow());
    }

    private StationInfo describe(KitchenStation station) {
        String restaurantName = restaurants.findById(station.getRestaurantId()).map(r -> r.getName()).orElse("");
        return new StationInfo(station.getId(), station.getName(), station.getRestaurantId(), restaurantName);
    }

    public record StationInfo(java.util.UUID stationId, String stationName, java.util.UUID restaurantId, String restaurantName) {
    }

    public record PairedDevice(String deviceToken, StationInfo station) {

        @Override
        public String toString() {
            return "PairedDevice[deviceToken=***, station=" + station + "]";
        }
    }
}
