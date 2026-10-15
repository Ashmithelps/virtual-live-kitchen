package com.virtualkitchen.hygiene.dto;

import java.util.UUID;

/** A dispute in the admin review queue with the result and the restaurant it's about. */
public record AdminDisputeResponse(HygieneDisputeResponse dispute, UUID restaurantId, String restaurantName, HygieneResultResponse result) {
}
