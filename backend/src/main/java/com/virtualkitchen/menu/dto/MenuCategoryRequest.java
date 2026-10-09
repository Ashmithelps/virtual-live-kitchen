package com.virtualkitchen.menu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MenuCategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 300) String description,
        @Min(0) @Max(10_000) Integer sortOrder) {
}
