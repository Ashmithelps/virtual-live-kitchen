package com.virtualkitchen.payment.razorpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/** Only validated when app.payment.provider=razorpay (see RazorpayPaymentProvider). */
@ConfigurationProperties(prefix = "app.payment.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret, URI apiBaseUrl) {

    @Override
    public String toString() {
        return "RazorpayProperties[keyId=" + keyId + ", keySecret=***, webhookSecret=***, apiBaseUrl=" + apiBaseUrl + "]";
    }
}
