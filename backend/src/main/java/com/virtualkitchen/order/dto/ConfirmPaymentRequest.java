package com.virtualkitchen.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Values returned by the gateway's checkout to the client. */
public record ConfirmPaymentRequest(
        @NotBlank @Size(max = 100) String providerPaymentId,
        @Size(max = 512) String signature) {
}
