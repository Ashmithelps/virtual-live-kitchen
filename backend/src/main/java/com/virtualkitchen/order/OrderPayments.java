package com.virtualkitchen.order;

import com.virtualkitchen.payment.PaymentProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Bridges orders and the {@link PaymentProvider}; all payment state changes on an order go through here. */
@Component
public class OrderPayments {

    private final PaymentProvider provider;
    private final Clock clock;

    public OrderPayments(PaymentProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    /** For ONLINE orders: registers the amount with the gateway. Returns data the client needs for checkout. */
    PaymentProvider.PaymentSession start(Order order) {
        PaymentProvider.PaymentSession session = provider.createPayment(order.getId(), order.getTotalPaise());
        order.startPayment(provider.name(), session.providerOrderId());
        return session;
    }

    /** @return the gateway's failure reason, or null on success */
    String confirm(Order order, String providerPaymentId, String signature) {
        PaymentProvider.PaymentVerification verification = provider.verify(
                order.getPaymentProviderOrderId(), providerPaymentId, signature, order.getTotalPaise());
        if (verification.success()) {
            order.markPaid(providerPaymentId, clock.instant());
            return null;
        }
        order.markPaymentFailed(providerPaymentId, clock.instant());
        return verification.failureReason();
    }

    /** Called after REJECTED / CANCELLED. Only captured online payments are refunded. */
    void refundIfPaid(Order order) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            String refundId = provider.refund(order.getPaymentProviderPaymentId(), order.getTotalPaise());
            order.markRefunded(refundId, clock.instant());
        }
    }
}
