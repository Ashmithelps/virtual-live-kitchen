package com.virtualkitchen.restaurant;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
public class Restaurant {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cuisine_tags", nullable = false, columnDefinition = "text[]")
    private String[] cuisineTags = new String[0];

    @ElementCollection
    @CollectionTable(name = "restaurant_opening_hours", joinColumns = @JoinColumn(name = "restaurant_id"))
    private List<OpeningSlot> openingHours = new ArrayList<>();

    @Column(name = "supports_dine_in", nullable = false)
    private boolean supportsDineIn;

    @Column(name = "supports_takeaway", nullable = false)
    private boolean supportsTakeaway;

    @Column(name = "supports_delivery", nullable = false)
    private boolean supportsDelivery;

    @Column(name = "delivery_radius_km")
    private Double deliveryRadiusKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RestaurantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Restaurant() {
        // for JPA
    }

    public Restaurant(UUID ownerId, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.status = RestaurantStatus.PENDING_APPROVAL;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Everything the owner may edit. Status is deliberately absent: only admins change it. */
    public void updateDetails(String name, String slug, String address, String city, double latitude,
                              double longitude, List<String> cuisineTags, List<OpeningSlot> openingHours,
                              Set<OrderType> orderTypes, Double deliveryRadiusKm, Instant now) {
        this.name = name;
        this.slug = slug;
        this.address = address;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        this.cuisineTags = cuisineTags.toArray(String[]::new);
        this.openingHours.clear();
        this.openingHours.addAll(openingHours);
        this.supportsDineIn = orderTypes.contains(OrderType.DINE_IN);
        this.supportsTakeaway = orderTypes.contains(OrderType.TAKEAWAY);
        this.supportsDelivery = orderTypes.contains(OrderType.DELIVERY);
        this.deliveryRadiusKm = supportsDelivery ? deliveryRadiusKm : null;
        this.updatedAt = now;
    }

    public void changeStatus(RestaurantStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public Set<OrderType> orderTypes() {
        Set<OrderType> types = EnumSet.noneOf(OrderType.class);
        if (supportsDineIn) types.add(OrderType.DINE_IN);
        if (supportsTakeaway) types.add(OrderType.TAKEAWAY);
        if (supportsDelivery) types.add(OrderType.DELIVERY);
        return types;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public List<String> getCuisineTags() { return List.of(cuisineTags); }
    public List<OpeningSlot> getOpeningHours() { return openingHours; }
    public Double getDeliveryRadiusKm() { return deliveryRadiusKm; }
    public RestaurantStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
