package com.virtualkitchen.restaurant.media;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.restaurant.RestaurantAccess;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/owner/restaurants/{restaurantId}/uploads")
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class OwnerUploadController {

    private final RestaurantAccess restaurantAccess;
    private final ObjectStorage objectStorage;

    public OwnerUploadController(RestaurantAccess restaurantAccess, ObjectStorage objectStorage) {
        this.restaurantAccess = restaurantAccess;
        this.objectStorage = objectStorage;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse createUpload(@AuthenticationPrincipal AuthenticatedUser owner,
                                       @PathVariable UUID restaurantId,
                                       @Valid @RequestBody UploadRequest request) {
        restaurantAccess.requireOwned(restaurantId, owner.id());
        String key = MediaKeys.newKey(restaurantId, request.purpose(), request.contentType());
        ObjectStorage.PresignedUpload upload = objectStorage.presignUpload(key, request.contentType());
        return new UploadResponse(key, upload.url(), "PUT", upload.headers(), upload.expiresAt());
    }
}
