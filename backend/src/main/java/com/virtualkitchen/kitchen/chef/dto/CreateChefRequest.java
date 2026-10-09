package com.virtualkitchen.kitchen.chef.dto;

import com.virtualkitchen.auth.user.PhoneNumbers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates a CHEF login plus their kitchen profile. The owner sets an initial password and shares
 * it with the chef. {@code displayName} defaults to {@code name}; {@code photoKey} comes from an
 * upload with purpose CHEF_PHOTO.
 */
public record CreateChefRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = PhoneNumbers.E164_REGEX, message = PhoneNumbers.E164_MESSAGE) String phone,
        @NotBlank @Size(min = 8, max = 72) String initialPassword,
        @Size(max = 80) String displayName,
        @Size(max = 300) String photoKey,
        @Size(max = 500) String bio,
        @Size(max = 80) String specialty) {

    @Override
    public String toString() {
        return "CreateChefRequest[name=" + name + ", phone=" + phone + "]";
    }
}
