package com.virtualkitchen.payment.razorpay;

import com.virtualkitchen.payment.PaymentProvider;
import com.virtualkitchen.payment.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Razorpay Orders + Checkout. Use rzp_test_ keys in development; live keys are logged loudly.
 * Docs: Orders API, Payments API (fetch/capture/refund), Checkout signature, Webhooks.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpayPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentProvider.class);

    private final RazorpayProperties properties;
    private final RazorpayClient client;

    public RazorpayPaymentProvider(RazorpayProperties properties) {
        requireSet(properties.keyId(), "RAZORPAY_KEY_ID");
        requireSet(properties.keySecret(), "RAZORPAY_KEY_SECRET");
        requireSet(properties.webhookSecret(), "RAZORPAY_WEBHOOK_SECRET");
        if (properties.apiBaseUrl() == null) {
            throw new IllegalStateException("RAZORPAY_API_BASE_URL must be set");
        }
        if (properties.keyId().startsWith("rzp_live_")) {
            log.warn("Razorpay LIVE keys are configured: real money will be charged");
        } else if (!properties.keyId().startsWith("rzp_test_")) {
            log.warn("RAZORPAY_KEY_ID doesn't look like a Razorpay key (expected rzp_test_… or rzp_live_…)");
        }
        this.properties = properties;
        this.client = new RazorpayClient(properties);
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public ProviderOrder createOrder(UUID orderId, long amountPaise, String receipt) {
        RazorpayClient.RzpOrder order = client.createOrder(amountPaise, receipt, Map.of("order_id", orderId.toString()));
        if (order.amount() != amountPaise) {
            throw new PaymentProviderException("Payment provider created an order for the wrong amount");
        }
        return new ProviderOrder(order.id());
    }

    @Override
    public String publicKeyId() {
        return properties.keyId();
    }

    /**
     * Never trusts the browser: checks the signature, then asks Razorpay for the payment and
     * confirms it belongs to this order and amount. Authorised-but-not-captured payments (when
     * auto-capture is off in the dashboard) are captured here.
     */
    @Override
    public CheckoutResult verifyCheckout(String providerOrderId, String providerPaymentId, String signature, long expectedAmountPaise) {
        if (!RazorpaySignatures.checkoutValid(providerOrderId, providerPaymentId, signature, properties.keySecret())) {
            return CheckoutResult.invalid("Payment signature is invalid");
        }
        RazorpayClient.RzpPayment payment = client.fetchPayment(providerPaymentId);
        if (!providerOrderId.equals(payment.orderId())) {
            return CheckoutResult.invalid("Payment belongs to a different order");
        }
        return switch (payment.status()) {
            case "captured" -> CheckoutResult.captured(payment.id(), payment.amount());
            case "authorized" -> {
                if (payment.amount() != expectedAmountPaise) {
                    yield CheckoutResult.failed(payment.id(), "Authorised amount does not match the order");
                }
                RazorpayClient.RzpPayment captured = client.capture(payment.id(), expectedAmountPaise);
                yield "captured".equals(captured.status())
                        ? CheckoutResult.captured(captured.id(), captured.amount())
                        : CheckoutResult.pending(captured.id());
            }
            case "failed" -> CheckoutResult.failed(payment.id(), payment.errorDescription());
            default -> CheckoutResult.pending(payment.id());
        };
    }

    @Override
    public Refund refund(String providerPaymentId, long amountPaise, String receipt) {
        RazorpayClient.RzpRefund refund = client.refund(providerPaymentId, amountPaise, receipt);
        return new Refund(refund.id(), "processed".equals(refund.status()));
    }

    @Override
    public Optional<Refund> findRefund(String providerPaymentId) {
        return client.refunds(providerPaymentId).stream()
                .filter(r -> !"failed".equals(r.status()))
                .findFirst()
                .map(r -> new Refund(r.id(), "processed".equals(r.status())));
    }

    String webhookSecret() {
        return properties.webhookSecret();
    }

    private static void requireSet(String value, String env) {
        if (value == null || value.isBlank() || value.startsWith("change_me")) {
            throw new IllegalStateException(env + " must be set when PAYMENT_PROVIDER=razorpay");
        }
    }
}
