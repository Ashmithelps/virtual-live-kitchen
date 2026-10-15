package com.virtualkitchen.hygiene.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UPHOLD needs either {@code overrideScore} (the corrected 0–100 score) or {@code exclude = true} (leave the clip out
 * of every average). REJECT keeps the result as it is.
 */
public record ResolveDisputeRequest(@NotNull Decision decision, @DecimalMin("0") @DecimalMax("100") Double overrideScore, Boolean exclude,
                                    @Size(max = 500) String note) {

    public enum Decision { UPHOLD, REJECT }
}
