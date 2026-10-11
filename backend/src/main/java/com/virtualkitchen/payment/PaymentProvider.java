package com.virtualkitchen.payment;

import java.util.Optional;
import java.util.UUID;

/**
 * A payment gateway. Flow (Razorpay-style):
 * <ol>
 *   <li>{@link #createOrder}: register the amount; the client opens the gateway's checkout with it.</li>
 *   <li>{@link #verifyCheckout}: check what the client reports back (signature + the gateway's own
 *       record of the payment) and capture it if it was only authorised.</li>
 *   <li>Webhooks (provider-specific controller) confirm captures, failures and refunds later on.</li>
 * </ol>
 * Implementations make network calls; callers must not hold database locks while calling them.
 */
public interface PaymentProvider {

    /** Stored on each payment, e.g. "mock" or "razorpay". */
    String name();

    /** @throws PaymentProviderException if the gateway is unavailable */
    ProviderOrder createOrder(UUID orderId, long amountPaise, String receipt);

    CheckoutResult verifyCheckout(String providerOrderId, String providerPaymentId, String signature, long expectedAmountPaise);

    /** Full refund of a captured payment. @throws PaymentProviderException if not accepted */
    Refund refund(String providerPaymentId, long amountPaise, String receipt);

    /** An existing refund for this payment, so a retried refund never refunds twice. */
    Optional<Refund> findRefund(String providerPaymentId);

    /** Public key the browser checkout needs (Razorpay key id); null when there is none. */
    default String publicKeyId() {
        return null;
    }

    record ProviderOrder(String providerOrderId) {
    }

    record CheckoutResult(Outcome outcome, String providerPaymentId, Long capturedAmountPaise, String reason) {

        public enum Outcome {
            CAPTURED,
            FAILED,
            /** Signature or order/payment mismatch: a forged or mixed-up confirmation. Nothing changes. */
            INVALID,
            /** Gateway hasn't settled it yet; a webhook will. */
            PENDING
        }

        public static CheckoutResult captured(String paymentId, long amountPaise) {
            return new CheckoutResult(Outcome.CAPTURED, paymentId, amountPaise, null);
        }

        public static CheckoutResult failed(String paymentId, String reason) {
            return new CheckoutResult(Outcome.FAILED, paymentId, null, reason);
        }

        public static CheckoutResult invalid(String reason) {
            return new CheckoutResult(Outcome.INVALID, null, null, reason);
        }

        public static CheckoutResult pending(String paymentId) {
            return new CheckoutResult(Outcome.PENDING, paymentId, null, null);
        }
    }

    /** @param processed true when the money has already gone back (no webhook to wait for) */
    record Refund(String refundId, boolean processed) {
    }
}
