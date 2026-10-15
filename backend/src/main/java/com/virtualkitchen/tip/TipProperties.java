package com.virtualkitchen.tip;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Tip limits (paise) and the platform's share in basis points (0 = everything goes to the chef). */
@Validated
@ConfigurationProperties(prefix = "app.tips")
public record TipProperties(@Min(0) @Max(5000) int platformFeeBps, @Min(100) long minPaise, @Min(100) long maxPaise,
                            @NotEmpty List<Long> presetsPaise) {

    long feeOn(long amountPaise) {
        return amountPaise * platformFeeBps / 10_000;
    }
}
