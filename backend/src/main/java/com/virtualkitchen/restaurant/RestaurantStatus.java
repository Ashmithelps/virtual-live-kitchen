package com.virtualkitchen.restaurant;

public enum RestaurantStatus {
    /** Newly created; invisible to customers until an admin approves it. */
    PENDING_APPROVAL,
    ACTIVE,
    SUSPENDED
}
