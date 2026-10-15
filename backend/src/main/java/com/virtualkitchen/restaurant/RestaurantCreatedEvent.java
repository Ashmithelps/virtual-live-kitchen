package com.virtualkitchen.restaurant;

import java.util.UUID;

/** Published inside the transaction that creates a restaurant (billing starts its free trial). */
public record RestaurantCreatedEvent(UUID restaurantId) {
}
