package com.virtualkitchen.auth.security;

import com.virtualkitchen.auth.user.Role;
import com.virtualkitchen.auth.user.UserStatus;

import java.util.UUID;

/** Principal placed in the SecurityContext from a verified access token. */
public record AuthenticatedUser(UUID id, Role role, UserStatus status) {
}
