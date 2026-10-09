package com.virtualkitchen.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of /api/auth/refresh and /api/auth/logout. */
public record RefreshTokenRequest(@NotBlank @Size(max = 256) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[***]";
    }
}
