package com.virtualkitchen.kitchen;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "station_assignments")
public class StationAssignment {

    @EmbeddedId
    private Key id;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected StationAssignment() {
        // for JPA
    }

    public StationAssignment(UUID stationId, UUID chefId, UUID restaurantId, Instant now) {
        this.id = new Key(stationId, chefId);
        this.restaurantId = restaurantId;
        this.assignedAt = now;
    }

    public UUID getStationId() { return id.stationId; }
    public UUID getChefId() { return id.chefId; }
    public UUID getRestaurantId() { return restaurantId; }
    public Instant getAssignedAt() { return assignedAt; }

    @Embeddable
    public record Key(@Column(name = "station_id") UUID stationId,
                      @Column(name = "chef_id") UUID chefId) implements Serializable {
    }
}
