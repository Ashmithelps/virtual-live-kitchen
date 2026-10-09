package com.virtualkitchen.auth.credential;

import com.virtualkitchen.auth.user.User;

/**
 * Verifies one kind of {@link LoginCredentials}. To add OTP login: add an {@code OtpCredentials}
 * record and an {@code OtpCredentialVerifier} bean — no change to AuthService or AuthController.
 */
public interface CredentialVerifier {

    boolean supports(LoginCredentials credentials);

    /** @throws InvalidCredentialsException if the credentials do not identify a user */
    User verify(LoginCredentials credentials);
}
