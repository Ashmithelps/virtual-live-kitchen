package com.virtualkitchen.restaurant.dto;

import com.virtualkitchen.restaurant.OrderType;

import java.util.List;
import java.util.Set;

/** One row of the public restaurant listing. */
public record RestaurantSummaryResponse(
        String slug,
        String name,
        String address,
        String city,
        double latitude,
        double longitude,
        List<String> cuisineTags,
        Set<OrderType> orderTypes,
        Double deliveryRadiusKm,
        boolean pureVeg,
        boolean openNow) {
}
