package com.virtualkitchen.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(
        @Min(0) @Max(10_000) int taxRateBasisPoints,
        @Min(1) int maxLines,
        @Min(1) int maxQuantityPerLine) {

    /** Integer paise maths, rounded half-up: 5% of ₹498.00 (49800) = 2490. */
    public long taxOn(long subtotalPaise) {
        return Math.addExact(Math.multiplyExact(subtotalPaise, (long) taxRateBasisPoints), 5_000L) / 10_000L;
    }
}
