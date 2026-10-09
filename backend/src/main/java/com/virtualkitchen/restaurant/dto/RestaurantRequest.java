package com.virtualkitchen.restaurant.dto;

import com.virtualkitchen.restaurant.OrderType;
import com.virtualkitchen.restaurant.Slugs;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/**
 * Create/replace a restaurant. {@code slug} is optional: generated from name + city when absent.
 * {@code deliveryRadiusKm} is required when {@code orderTypes} contains DELIVERY.
 */
public record RestaurantRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(min = 3, max = 80) @Pattern(regexp = Slugs.REGEX, message = Slugs.MESSAGE) String slug,
        @NotBlank @Size(max = 300) String address,
        @NotBlank @Size(max = 80) String city,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> cuisineTags,
        @NotNull @Size(max = 28) List<@NotNull @Valid OpeningSlotDto> openingHours,
        @NotEmpty Set<@NotNull OrderType> orderTypes,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("50") Double deliveryRadiusKm) {
}
