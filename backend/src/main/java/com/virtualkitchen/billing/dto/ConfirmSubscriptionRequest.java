package com.virtualkitchen.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmSubscriptionRequest(@NotBlank @Size(max = 100) String subscriptionId, @NotBlank @Size(max = 100) String paymentId,
                                         @Size(max = 512) String signature) {
}
