package com.virtualkitchen.clip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clips")
public class Clip {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false, unique = true)
    private UUID orderId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "chef_id", updatable = false)
    private UUID chefId;

    @Column(name = "station_id", updatable = false)
    private UUID stationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClipState status;

    @Column(name = "duration_s", precision = 6, scale = 2)
    private BigDecimal durationSeconds;

    @Column(name = "video_key", length = 300)
    private String videoKey;

    @Column(name = "thumb_key", length = 300)
    private String thumbKey;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "ready_at") private Instant readyAt;
    @Column(name = "expires_at") private Instant expiresAt;

    protected Clip() {
        // for JPA
    }

    Clip(UUID orderId, UUID restaurantId, UUID chefId, UUID stationId, ClipState status, Instant now) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.restaurantId = restaurantId;
        this.chefId = chefId;
        this.stationId = stationId;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void moveTo(ClipState status, String reason, Instant now) {
        this.status = status;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 300));
        this.updatedAt = now;
    }

    void markReady(String videoKey, String thumbKey, double durationSeconds, Instant expiresAt, Instant now) {
        this.status = ClipState.READY;
        this.videoKey = videoKey;
        this.thumbKey = thumbKey;
        this.durationSeconds = BigDecimal.valueOf(durationSeconds).setScale(2, RoundingMode.HALF_UP);
        this.failureReason = null;
        this.readyAt = now;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    void markExpired(Instant now) {
        this.status = ClipState.EXPIRED;
        this.videoKey = null;
        this.thumbKey = null;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getChefId() { return chefId; }
    public UUID getStationId() { return stationId; }
    public ClipState getStatus() { return status; }
    public BigDecimal getDurationSeconds() { return durationSeconds; }
    public String getVideoKey() { return videoKey; }
    public String getThumbKey() { return thumbKey; }
    public String getFailureReason() { return failureReason; }
    public Instant getReadyAt() { return readyAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
