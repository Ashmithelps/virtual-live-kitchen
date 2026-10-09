package com.virtualkitchen.auth.dto;

import com.virtualkitchen.auth.user.PhoneNumbers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Pattern(regexp = PhoneNumbers.E164_REGEX, message = PhoneNumbers.E164_MESSAGE) String phone,
        @NotBlank @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "LoginRequest[phone=" + phone + "]";
    }
}
