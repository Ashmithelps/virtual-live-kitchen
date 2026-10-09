package com.virtualkitchen.payment;

import com.virtualkitchen.common.crypto.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Development stand-in for a real gateway. Every verification succeeds unless the payment id
 * starts with {@code "fail"}, so failures can be exercised too. It performs no real checks and
 * must never be active in production.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock")
public class MockPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentProvider.class);

    public MockPaymentProvider() {
        log.warn("MOCK payment provider is active: all payments are approved without charging anyone");
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public PaymentSession createPayment(UUID orderId, long amountPaise) {
        String providerOrderId = "mock_order_" + SecureTokens.random(12);
        return new PaymentSession(providerOrderId, Map.of(
                "provider", "mock",
                "providerOrderId", providerOrderId,
                "amountPaise", amountPaise,
                "currency", "INR",
                "instructions", "POST /api/orders/" + orderId + "/payment/confirm with any providerPaymentId "
                        + "(prefix it with 'fail' to simulate a failed payment)"));
    }

    @Override
    public PaymentVerification verify(String providerOrderId, String providerPaymentId, String signature, long amountPaise) {
        if (providerPaymentId.startsWith("fail")) {
            return PaymentVerification.failed("Payment declined (simulated)");
        }
        return PaymentVerification.ok();
    }

    @Override
    public String refund(String providerPaymentId, long amountPaise) {
        return "mock_refund_" + SecureTokens.random(12);
    }
}
