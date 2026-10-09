package com.virtualkitchen.order.dto;

import java.util.Map;

/** Present while an online payment is outstanding. {@code clientData} is only filled in on placement. */
public record PaymentInfo(String provider, String providerOrderId, long amountPaise, Map<String, Object> clientData) {
}
