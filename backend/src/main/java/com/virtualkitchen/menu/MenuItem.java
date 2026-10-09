package com.virtualkitchen.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Column(name = "is_veg", nullable = false)
    private boolean veg;

    /** Object key in the media bucket; never a URL. */
    @Column(name = "image_key", length = 300)
    private String imageKey;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MenuItem() {
        // for JPA
    }

    public MenuItem(UUID restaurantId, Instant now) {
        this.id = UUID.randomUUID();
        this.restaurantId = restaurantId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(UUID categoryId, String name, String description, long pricePaise, boolean veg,
                       String imageKey, boolean available, int sortOrder, Instant now) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.pricePaise = pricePaise;
        this.veg = veg;
        this.imageKey = imageKey;
        this.available = available;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public void setAvailable(boolean available, Instant now) {
        this.available = available;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getPricePaise() { return pricePaise; }
    public boolean isVeg() { return veg; }
    public String getImageKey() { return imageKey; }
    public boolean isAvailable() { return available; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
