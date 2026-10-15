package com.virtualkitchen.billing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Trial applies to restaurants created from now on; grace to the next payment failure. */
public record BillingSettingsRequest(@NotNull @Min(0) @Max(365) Integer trialDays, @NotNull @Min(0) @Max(60) Integer graceDays) {
}
