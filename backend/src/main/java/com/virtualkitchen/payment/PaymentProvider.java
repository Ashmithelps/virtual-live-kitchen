package com.virtualkitchen.payment;

import java.util.Map;
import java.util.UUID;

/**
 * A payment gateway, shaped after Razorpay/Stripe-style flows:
 * <ol>
 *   <li>{@link #createPayment} registers the amount with the gateway; the client gets
 *       {@link PaymentSession#clientData()} to open the gateway's checkout.</li>
 *   <li>After checkout the client sends back the gateway's payment id + signature, which the
 *       server checks with {@link #verify} before marking the order PAID.</li>
 * </ol>
 */
public interface PaymentProvider {

    /** Stored on the order, e.g. "mock" or "razorpay". */
    String name();

    PaymentSession createPayment(UUID orderId, long amountPaise);

    PaymentVerification verify(String providerOrderId, String providerPaymentId, String signature, long amountPaise);

    /** Full refund of a captured payment. */
    String refund(String providerPaymentId, long amountPaise);

    record PaymentSession(String providerOrderId, Map<String, Object> clientData) {
    }

    record PaymentVerification(boolean success, String failureReason) {

        public static PaymentVerification ok() {
            return new PaymentVerification(true, null);
        }

        public static PaymentVerification failed(String reason) {
            return new PaymentVerification(false, reason);
        }
    }
}
