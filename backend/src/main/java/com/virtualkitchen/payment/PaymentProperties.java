package com.virtualkitchen.payment;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(@NotBlank String provider, @NotBlank String merchantName) {
}
