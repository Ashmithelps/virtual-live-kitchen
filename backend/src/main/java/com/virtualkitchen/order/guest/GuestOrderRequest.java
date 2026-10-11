package com.virtualkitchen.order.guest;

import com.virtualkitchen.auth.user.PhoneNumbers;
import com.virtualkitchen.order.PaymentMethod;
import com.virtualkitchen.order.dto.OrderLineRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Dine-in order without an account. Restaurant and table come from {@code tableSessionToken};
 * the order type is always DINE_IN. {@code paymentMethod} defaults to ONLINE.
 */
public record GuestOrderRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = PhoneNumbers.E164_REGEX, message = PhoneNumbers.E164_MESSAGE) String phone,
        @NotBlank @Size(max = 1000) String tableSessionToken,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid OrderLineRequest> items,
        PaymentMethod paymentMethod,
        @Size(max = 500) String notes) {
}
