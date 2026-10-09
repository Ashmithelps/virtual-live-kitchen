package com.virtualkitchen.order.dto;

import java.util.UUID;

public record OrderItemResponse(UUID menuItemId, String name, long unitPricePaise, boolean veg, int quantity,
                                long lineTotalPaise, String notes) {
}
