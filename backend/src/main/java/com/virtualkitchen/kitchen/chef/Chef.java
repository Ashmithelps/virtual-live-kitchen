package com.virtualkitchen.kitchen.chef;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Public-facing kitchen profile of a CHEF user, tied to exactly one restaurant. */
@Entity
@Table(name = "chefs")
public class Chef {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "photo_key", length = 300)
    private String photoKey;

    @Column(length = 500)
    private String bio;

    @Column(length = 80)
    private String specialty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Soft delete: the row stays for consent history and (later) clips the chef appears in. */
    @Column(name = "removed_at")
    private Instant removedAt;

    protected Chef() {
        // for JPA
    }

    public Chef(UUID userId, UUID restaurantId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.restaurantId = restaurantId;
        this.createdAt = now;
    }

    public void updateProfile(String displayName, String photoKey, String bio, String specialty) {
        this.displayName = displayName;
        this.photoKey = photoKey;
        this.bio = bio;
        this.specialty = specialty;
    }

    public void remove(Instant now) {
        this.removedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getDisplayName() { return displayName; }
    public String getPhotoKey() { return photoKey; }
    public String getBio() { return bio; }
    public String getSpecialty() { return specialty; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRemovedAt() { return removedAt; }
}
