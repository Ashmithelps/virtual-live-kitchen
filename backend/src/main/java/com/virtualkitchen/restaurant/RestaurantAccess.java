package com.virtualkitchen.restaurant;

import com.virtualkitchen.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ownership guard used by every owner-scoped operation in every module (menu, tables, kitchen,
 * uploads). Answers 404 rather than 403 for restaurants owned by someone else, so the existence
 * of other restaurants' ids isn't confirmed.
 */
@Component
public class RestaurantAccess {

    private final RestaurantRepository repository;

    public RestaurantAccess(RestaurantRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Restaurant requireOwned(UUID restaurantId, UUID ownerId) {
        return repository.findByIdAndOwnerId(restaurantId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Restaurant not found"));
    }
}
