package com.virtualkitchen.tip.dto;

import com.virtualkitchen.tip.TipSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Amount in paise (₹50 = 5000); limits come from the tip options. */
public record TipRequest(@NotNull UUID chefId, @NotNull @Positive Long amountPaise, @NotNull TipSource source) {
}
