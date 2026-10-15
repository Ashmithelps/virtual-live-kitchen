package com.virtualkitchen.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** What a plan includes. Stored as JSON so new features don't need a migration. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanFeatures(
        @NotNull @Min(1) @Max(500) Integer maxStations,
        @NotNull @Min(1) @Max(3650) Integer clipRetentionDays,
        @NotNull Boolean analytics) {
}
