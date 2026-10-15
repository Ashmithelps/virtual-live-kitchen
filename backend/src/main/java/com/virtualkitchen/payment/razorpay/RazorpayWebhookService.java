package com.virtualkitchen.payment.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.common.crypto.SecureTokens;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.billing.BillingService;
import com.virtualkitchen.order.OrderPaymentService;
import com.virtualkitchen.tip.PayoutAccountService;
import com.virtualkitchen.tip.TipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;

/**
 * Razorpay webhooks: payment.captured / payment.failed (food orders and tips), refund.processed, Route
 * transfer.processed / transfer.failed (tips reaching the chef), product.route.* (chef linked-account activation) and
 * subscription.* (restaurant billing: authenticated, activated, charged, pending, halted, cancelled, completed).
 *
 * <p>Idempotent: the event id (X-Razorpay-Event-Id) is inserted into payment_webhook_events in
 * the same transaction as its effect. A redelivery hits the primary key and is acknowledged
 * without being applied again; if applying fails, both roll back and Razorpay retries.
 */
@Service
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);
    private static final String PROVIDER = "razorpay";

    private final RazorpayPaymentProvider provider;
    private final OrderPaymentService payments;
    private final TipService tips;
    private final PayoutAccountService payoutAccounts;
    private final BillingService billing;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public RazorpayWebhookService(RazorpayPaymentProvider provider, OrderPaymentService payments, TipService tips,
                                  PayoutAccountService payoutAccounts, BillingService billing, JdbcTemplate jdbc, ObjectMapper json,
                                  Clock clock) {
        this.billing = billing;
        this.provider = provider;
        this.payments = payments;
        this.tips = tips;
        this.payoutAccounts = payoutAccounts;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public enum Result { APPLIED, DUPLICATE, IGNORED }

    @Transactional
    public Result handle(String rawBody, String signature, String eventIdHeader) {
        if (!RazorpaySignatures.webhookValid(rawBody, signature, provider.webhookSecret())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
        }
        JsonNode event;
        try {
            event = json.readTree(rawBody);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Webhook body is not JSON");
        }
        String type = event.path("event").asText("");
        // Razorpay sends a unique X-Razorpay-Event-Id per event; fall back to the body hash.
        String eventId = eventIdHeader != null && !eventIdHeader.isBlank() ? eventIdHeader : "sha256:" + SecureTokens.sha256Hex(rawBody);

        int inserted = jdbc.update("""
                INSERT INTO payment_webhook_events (provider, event_id, event_type, payload, received_at)
                VALUES (?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (provider, event_id) DO NOTHING
                """, PROVIDER, eventId, type, rawBody, Timestamp.from(clock.instant()));
        if (inserted == 0) {
            log.info("Razorpay webhook {} ({}) already processed", eventId, type);
            return Result.DUPLICATE;
        }

        JsonNode payment = event.path("payload").path("payment").path("entity");
        JsonNode refund = event.path("payload").path("refund").path("entity");
        JsonNode transfer = event.path("payload").path("transfer").path("entity");
        JsonNode subscription = event.path("payload").path("subscription").path("entity");
        JsonNode product = event.path("payload").path("merchant_product").path("entity");
        String gatewayOrderId = payment.path("order_id").asText();
        boolean known = switch (type) {
            case "payment.captured" -> tips.applyCaptured(PROVIDER, gatewayOrderId, payment.path("id").asText(), payment.path("amount").asLong())
                    || payments.applyCaptured(PROVIDER, gatewayOrderId, payment.path("id").asText(), payment.path("amount").asLong(), rawBody);
            case "payment.failed" -> tips.applyFailed(PROVIDER, gatewayOrderId, payment.path("id").asText(), payment.path("error_description").asText(null))
                    || payments.applyFailed(PROVIDER, gatewayOrderId, payment.path("id").asText(), payment.path("error_description").asText(null), rawBody);
            case "transfer.processed" -> tips.applyTransferProcessed(PROVIDER, transfer.path("source").asText(), transfer.path("id").asText());
            case "transfer.failed" -> tips.applyTransferFailed(PROVIDER, transfer.path("source").asText(),
                    transfer.path("error").path("description").asText("Transfer failed"));
            case "refund.processed" -> payments.applyRefundProcessed(PROVIDER, refund.path("payment_id").asText(),
                    refund.path("id").asText(), rawBody);
            case "subscription.authenticated" -> billing.applyAuthenticated(PROVIDER, subscription.path("id").asText());
            case "subscription.activated" -> billing.applyActivated(PROVIDER, subscription.path("id").asText(),
                    epoch(subscription.path("current_start")), epoch(subscription.path("current_end")));
            case "subscription.charged" -> billing.applyCharged(PROVIDER, subscription.path("id").asText(), payment.path("id").asText(null),
                    payment.path("amount").asLong(), epoch(subscription.path("current_start")), epoch(subscription.path("current_end")));
            case "subscription.pending", "subscription.halted" -> billing.applyPaymentFailed(PROVIDER, subscription.path("id").asText(),
                    type.substring("subscription.".length()));
            case "subscription.cancelled", "subscription.completed" -> billing.applyCancelled(PROVIDER, subscription.path("id").asText(),
                    type.substring("subscription.".length()));
            default -> {
                if (type.startsWith("product.route.")) {
                    String status = product.path("activation_status").asText(type.substring("product.route.".length()));
                    yield payoutAccounts.applyActivation(blankToNull(event.path("account_id").asText(null)),
                            blankToNull(product.path("id").asText(null)), status);
                }
                log.info("Ignoring Razorpay webhook event {}", type);
                yield false;
            }
        };
        return known ? Result.APPLIED : Result.IGNORED;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static java.time.Instant epoch(JsonNode seconds) {
        return seconds.isNumber() && seconds.asLong() > 0 ? java.time.Instant.ofEpochSecond(seconds.asLong()) : null;
    }
}
