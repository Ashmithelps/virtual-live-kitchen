package com.virtualkitchen.billing.dto;

import com.virtualkitchen.billing.PlanFeatures;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code code} is set once at creation (ignored on update). Price in paise, excluding GST. */
public record PlanRequest(
        @Pattern(regexp = "^[a-z0-9-]{2,40}$", message = "lowercase letters, digits and dashes") String code,
        @NotBlank @Size(max = 80) String name,
        @NotNull @Min(0) @Max(100_000_000) Long pricePaise,
        @NotNull @Valid PlanFeatures features,
        @NotNull Boolean active,
        @NotNull @Min(0) @Max(1000) Integer sortOrder) {
}
