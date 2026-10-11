package com.virtualkitchen.payment.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.common.crypto.SecureTokens;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.order.OrderPaymentService;
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
 * Razorpay webhooks: payment.captured, payment.failed, refund.processed.
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
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public RazorpayWebhookService(RazorpayPaymentProvider provider, OrderPaymentService payments, JdbcTemplate jdbc,
                                  ObjectMapper json, Clock clock) {
        this.provider = provider;
        this.payments = payments;
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
        boolean known = switch (type) {
            case "payment.captured" -> payments.applyCaptured(PROVIDER, payment.path("order_id").asText(),
                    payment.path("id").asText(), payment.path("amount").asLong(), rawBody);
            case "payment.failed" -> payments.applyFailed(PROVIDER, payment.path("order_id").asText(),
                    payment.path("id").asText(), payment.path("error_description").asText(null), rawBody);
            case "refund.processed" -> payments.applyRefundProcessed(PROVIDER, refund.path("payment_id").asText(),
                    refund.path("id").asText(), rawBody);
            default -> {
                log.info("Ignoring Razorpay webhook event {}", type);
                yield false;
            }
        };
        return known ? Result.APPLIED : Result.IGNORED;
    }
}
