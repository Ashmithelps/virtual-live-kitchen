package com.virtualkitchen.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record OrderItemResponse(@Schema(nullable = true) UUID menuItemId, String name, long unitPricePaise, boolean veg, int quantity,
                                long lineTotalPaise, @Schema(nullable = true) String notes) {
}
