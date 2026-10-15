package com.virtualkitchen.tip.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A chef's payout details. Forwarded to the gateway and never stored (only masked fragments are kept).
 * PAN must be an individual's (4th letter P); the bank account must be in the chef's own name.
 */
public record PayoutAccountRequest(
        @NotBlank @Size(min = 2, max = 120) String legalName,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = "^[0-9]{9,18}$", message = "must be 9 to 18 digits") String bankAccountNumber,
        @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "must be a valid IFSC, e.g. HDFC0001234") String ifsc,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}P[A-Z][0-9]{4}[A-Z]$", message = "must be an individual's PAN, e.g. ABCPE1234F") String pan,
        @NotBlank @Size(max = 100) String street,
        @NotBlank @Size(max = 60) String city,
        @NotBlank @Size(max = 60) String state,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit PIN code") String postalCode) {

    @Override
    public String toString() {
        return "PayoutAccountRequest[legalName=" + legalName + ", bankAccountNumber=****, ifsc=****, pan=****]";
    }
}
