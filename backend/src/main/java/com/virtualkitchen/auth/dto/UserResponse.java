package com.virtualkitchen.auth.dto;

import com.virtualkitchen.auth.user.Role;
import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id, String name, String phone, String email, Role role, UserStatus status, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getPhone(), user.getEmail(),
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
