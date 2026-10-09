package com.virtualkitchen.restaurant.table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dining_tables")
public class DiningTable {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(nullable = false, length = 20)
    private String label;

    /** Encoded in the table's QR code; regenerating it invalidates printed codes. */
    @Column(name = "qr_token", nullable = false, unique = true, length = 64)
    private String qrToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DiningTable() {
        // for JPA
    }

    public DiningTable(UUID restaurantId, String label, String qrToken, Instant now) {
        this.id = UUID.randomUUID();
        this.restaurantId = restaurantId;
        this.label = label;
        this.qrToken = qrToken;
        this.createdAt = now;
    }

    public void rename(String label) {
        this.label = label;
    }

    public void replaceQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getLabel() { return label; }
    public String getQrToken() { return qrToken; }
    public Instant getCreatedAt() { return createdAt; }
}
