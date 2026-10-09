package com.virtualkitchen.auth.dto;

import com.virtualkitchen.auth.AuthService.AuthResult;

import java.time.Instant;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserResponse user) {

    public static AuthResponse from(AuthResult result) {
        var tokens = result.tokens();
        return new AuthResponse("Bearer", tokens.accessToken(), tokens.accessTokenExpiresAt(),
                tokens.refreshToken(), tokens.refreshTokenExpiresAt(), UserResponse.from(result.user()));
    }
}
