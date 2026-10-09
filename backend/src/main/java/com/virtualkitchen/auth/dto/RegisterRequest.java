package com.virtualkitchen.auth.dto;

import com.virtualkitchen.auth.user.PhoneNumbers;
import com.virtualkitchen.auth.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code role} is optional (defaults to CUSTOMER); only CUSTOMER and RESTAURANT_OWNER may self-register. */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = PhoneNumbers.E164_REGEX, message = PhoneNumbers.E164_MESSAGE) String phone,
        @Email @Size(max = 254) String email,
        // BCrypt only uses the first 72 bytes.
        @NotBlank @Size(min = 8, max = 72) String password,
        Role role) {

    @Override
    public String toString() {
        return "RegisterRequest[name=" + name + ", phone=" + phone + ", email=" + email + ", role=" + role + "]";
    }
}
