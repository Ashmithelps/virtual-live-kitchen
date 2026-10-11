package com.virtualkitchen.payment.razorpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Razorpay's HMAC-SHA256 signatures, compared in constant time. */
public final class RazorpaySignatures {

    private RazorpaySignatures() {
    }

    /** Checkout: HMAC(key_secret, "{razorpay_order_id}|{razorpay_payment_id}") == razorpay_signature. */
    public static boolean checkoutValid(String providerOrderId, String providerPaymentId, String signature, String keySecret) {
        return matches(hmacHex(keySecret, providerOrderId + "|" + providerPaymentId), signature);
    }

    /** Webhooks: HMAC(webhook_secret, raw request body) == X-Razorpay-Signature. */
    public static boolean webhookValid(String rawBody, String signature, String webhookSecret) {
        return matches(hmacHex(webhookSecret, rawBody), signature);
    }

    static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static boolean matches(String expectedHex, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.US_ASCII), actual.strip().toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
}
