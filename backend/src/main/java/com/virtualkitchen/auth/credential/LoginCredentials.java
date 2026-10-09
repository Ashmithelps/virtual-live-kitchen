package com.virtualkitchen.auth.credential;

/**
 * Proof of identity presented at login. Each login method (password today, OTP later) is a
 * separate implementation handled by its own {@link CredentialVerifier}; {@code AuthService}
 * only dispatches, rate-limits by phone, and issues tokens.
 */
public interface LoginCredentials {

    String phone();
}
