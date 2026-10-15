package com.virtualkitchen.restaurant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.virtualkitchen.restaurant.OrderType;
import com.virtualkitchen.restaurant.Restaurant;
import com.virtualkitchen.restaurant.RestaurantStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owner/admin view of a restaurant. */
public record RestaurantResponse(
        UUID id,
        UUID ownerId,
        String name,
        String slug,
        String address,
        String city,
        double latitude,
        double longitude,
        List<String> cuisineTags,
        List<OpeningSlotDto> openingHours,
        Set<OrderType> orderTypes,
        @Schema(nullable = true) Double deliveryRadiusKm,
        RestaurantStatus status,
        @Schema(nullable = true, description = "ADMIN or BILLING when SUSPENDED") com.virtualkitchen.restaurant.SuspensionReason suspendedReason,
        @Schema(nullable = true) String logoKey,
        Instant createdAt,
        Instant updatedAt) {

    public static RestaurantResponse from(Restaurant r) {
        return new RestaurantResponse(r.getId(), r.getOwnerId(), r.getName(), r.getSlug(), r.getAddress(), r.getCity(),
                r.getLatitude(), r.getLongitude(), r.getCuisineTags(),
                r.getOpeningHours().stream().map(OpeningSlotDto::from).toList(),
                r.orderTypes(), r.getDeliveryRadiusKm(), r.getStatus(), r.getSuspendedReason(), r.getLogoKey(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
