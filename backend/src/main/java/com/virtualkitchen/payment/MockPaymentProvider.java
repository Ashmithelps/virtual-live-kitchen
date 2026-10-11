package com.virtualkitchen.payment;

import com.virtualkitchen.common.crypto.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Development stand-in for a real gateway. Every checkout succeeds unless the payment id starts
 * with {@code "fail"}; refunds complete instantly; there are no webhooks. It verifies nothing and
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
    public ProviderOrder createOrder(UUID orderId, long amountPaise, String receipt) {
        return new ProviderOrder("mock_order_" + SecureTokens.random(12));
    }

    @Override
    public CheckoutResult verifyCheckout(String providerOrderId, String providerPaymentId, String signature, long expectedAmountPaise) {
        if (providerPaymentId.startsWith("fail")) {
            return CheckoutResult.failed(providerPaymentId, "Payment declined (simulated)");
        }
        return CheckoutResult.captured(providerPaymentId, expectedAmountPaise);
    }

    @Override
    public Refund refund(String providerPaymentId, long amountPaise, String receipt) {
        return new Refund("mock_refund_" + SecureTokens.random(12), true);
    }

    @Override
    public Optional<Refund> findRefund(String providerPaymentId) {
        return Optional.empty();
    }
}
