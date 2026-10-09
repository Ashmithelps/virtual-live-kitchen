package com.virtualkitchen.kitchen.chef.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChefRequest(
        @NotBlank @Size(max = 80) String displayName,
        @Size(max = 300) String photoKey,
        @Size(max = 500) String bio,
        @Size(max = 80) String specialty) {
}
