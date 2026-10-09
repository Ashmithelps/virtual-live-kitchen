package com.virtualkitchen.restaurant.table;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DiningTableRequest(
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 _-]*$", message = "may contain letters, digits, spaces, '-' and '_'")
        String label) {
}
