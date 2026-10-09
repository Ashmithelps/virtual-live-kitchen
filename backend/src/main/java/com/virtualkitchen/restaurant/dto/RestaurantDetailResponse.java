package com.virtualkitchen.restaurant.dto;

import com.virtualkitchen.menu.dto.MenuCategoryResponse;
import com.virtualkitchen.restaurant.OrderType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Public restaurant page: details plus the customer-visible menu. */
public record RestaurantDetailResponse(
        UUID id,
        String slug,
        String name,
        String address,
        String city,
        double latitude,
        double longitude,
        List<String> cuisineTags,
        List<OpeningSlotDto> openingHours,
        Set<OrderType> orderTypes,
        Double deliveryRadiusKm,
        boolean openNow,
        List<MenuCategoryResponse> menu) {
}
