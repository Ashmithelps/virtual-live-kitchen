package com.virtualkitchen.billing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Defaults for trial and grace (admins can change both at runtime) and the supplier details printed on GST invoices.
 */
@Validated
@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(
        @Min(0) @Max(365) int defaultTrialDays,
        @Min(0) @Max(60) int defaultGraceDays,
        @Min(0) @Max(2800) int gstRateBps,
        @NotBlank String sacCode,
        @NotBlank String invoicePrefix,
        @NotBlank String supplierLegalName,
        String supplierGstin,
        @NotBlank String supplierAddress,
        @NotBlank @Pattern(regexp = "^[0-9]{2}$") String supplierStateCode) {
}
