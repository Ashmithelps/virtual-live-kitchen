package com.virtualkitchen.kitchen.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptConsentRequest(
        @NotBlank @Size(max = 20) String consentVersion,
        @NotBlank @Size(min = 64, max = 64) String consentTextHash) {
}
