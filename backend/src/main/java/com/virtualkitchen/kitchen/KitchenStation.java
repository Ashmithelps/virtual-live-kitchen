package com.virtualkitchen.kitchen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kitchen_stations")
public class KitchenStation {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(nullable = false, length = 60)
    private String name;

    /** SHA-256 of the recording device's token (device auth arrives in a later milestone). */
    @Column(name = "device_token_hash", unique = true, length = 64)
    private String deviceTokenHash;

    /** SHA-256 of the one-time pairing code shown as a QR; cleared once used. */
    @Column(name = "pairing_code_hash", unique = true, length = 64)
    private String pairingCodeHash;

    @Column(name = "pairing_expires_at")
    private Instant pairingExpiresAt;

    @Column(name = "device_paired_at")
    private Instant devicePairedAt;

    /** Written by {@link KitchenStationRepository#touchDevice} (a bulk update), never through the entity. */
    @Column(name = "device_last_seen_at", insertable = false, updatable = false)
    private Instant deviceLastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KitchenStation() {
        // for JPA
    }

    public KitchenStation(UUID restaurantId, String name, Instant now) {
        this.id = UUID.randomUUID();
        this.restaurantId = restaurantId;
        this.name = name;
        this.createdAt = now;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void replaceDeviceTokenHash(String deviceTokenHash) {
        this.deviceTokenHash = deviceTokenHash;
    }

    public void startPairing(String codeHash, Instant expiresAt) {
        this.pairingCodeHash = codeHash;
        this.pairingExpiresAt = expiresAt;
    }

    /** Consumes the pairing code and installs the new device's token (unpairing any previous phone). */
    public void completePairing(String deviceTokenHash, Instant now) {
        this.deviceTokenHash = deviceTokenHash;
        this.devicePairedAt = now;
        this.pairingCodeHash = null;
        this.pairingExpiresAt = null;
    }

    public boolean hasDevice() {
        return deviceTokenHash != null;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public String getDeviceTokenHash() { return deviceTokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPairingExpiresAt() { return pairingExpiresAt; }
    public Instant getDevicePairedAt() { return devicePairedAt; }
    public Instant getDeviceLastSeenAt() { return deviceLastSeenAt; }
}
