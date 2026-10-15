package com.virtualkitchen.tip.dto;

import com.virtualkitchen.order.dto.PaymentCheckoutResponse;

/** Open the gateway with {@code checkout} (same as order payments), then POST its result to …/tips/{tipId}/confirm. */
public record TipCheckoutResponse(TipResponse tip, PaymentCheckoutResponse checkout) {
}
