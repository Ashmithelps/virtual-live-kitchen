package com.virtualkitchen.billing.dto;

import com.virtualkitchen.billing.PlanFeatures;

import java.util.UUID;

/** {@code totalPaise} = price + GST: what the restaurant is charged each month. */
public record PlanResponse(UUID id, String code, String name, long pricePaise, long gstPaise, long totalPaise, PlanFeatures features,
                           boolean active, int sortOrder) {
}
