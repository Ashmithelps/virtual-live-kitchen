package com.virtualkitchen.clip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "clip_requests")
public class ClipRequest {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false, unique = true)
    private UUID orderId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "station_id", updatable = false)
    private UUID stationId;

    @Column(name = "chef_id", updatable = false)
    private UUID chefId;

    @Column(name = "window_start", nullable = false, updatable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false, updatable = false)
    private Instant windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClipRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private NotAvailableReason reason;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "chunk_keys", nullable = false, columnDefinition = "text[]")
    private String[] chunkKeys = new String[0];

    @Column(name = "requested_at", nullable = false, updatable = false) private Instant requestedAt;
    @Column(name = "uploaded_at") private Instant uploadedAt;
    @Column(name = "raw_deleted_at") private Instant rawDeletedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ClipRequest() {
        // for JPA
    }

    ClipRequest(UUID orderId, UUID restaurantId, UUID stationId, UUID chefId, Instant windowStart, Instant windowEnd, Instant now) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.restaurantId = restaurantId;
        this.stationId = stationId;
        this.chefId = chefId;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.status = ClipRequestStatus.PENDING_UPLOAD;
        this.requestedAt = now;
        this.updatedAt = now;
    }

    void markNotAvailable(NotAvailableReason reason, Instant now) {
        this.status = ClipRequestStatus.NOT_AVAILABLE;
        this.reason = reason;
        this.updatedAt = now;
    }

    /** Idempotent: re-reporting keys merges them. */
    void markUploaded(List<String> keys, Instant now) {
        Set<String> merged = new LinkedHashSet<>(List.of(chunkKeys));
        merged.addAll(keys);
        this.chunkKeys = merged.toArray(String[]::new);
        this.status = ClipRequestStatus.UPLOADED;
        this.reason = null;
        if (uploadedAt == null) {
            uploadedAt = now;
        }
        this.updatedAt = now;
    }

    void markRawDeleted(Instant now) {
        this.rawDeletedAt = now;
        this.updatedAt = now;
    }

    public boolean overlaps(Instant start, Instant end) {
        return start.isBefore(windowEnd) && end.isAfter(windowStart);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getStationId() { return stationId; }
    public UUID getChefId() { return chefId; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public ClipRequestStatus getStatus() { return status; }
    public NotAvailableReason getReason() { return reason; }
    public List<String> getChunkKeys() { return List.of(chunkKeys); }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getRawDeletedAt() { return rawDeletedAt; }
}
