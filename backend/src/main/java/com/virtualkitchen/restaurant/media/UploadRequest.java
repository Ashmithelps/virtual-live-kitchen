package com.virtualkitchen.restaurant.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code contentType}: image/jpeg, image/png or image/webp. */
public record UploadRequest(@NotNull UploadPurpose purpose, @NotBlank String contentType) {
}
