package com.virtualkitchen.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    public static final String COUNTER = "counter";

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false, length = 32)
    private String provider;

    @Column(name = "provider_order_id", updatable = false, length = 100)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;

    @Column(name = "provider_refund_id", length = 100)
    private String providerRefundId;

    /** Always computed by the server from order_items; never taken from a client. */
    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentAttemptStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "counter_method", updatable = false, length = 10)
    private CounterMethod counterMethod;

    @Column(name = "recorded_by", updatable = false)
    private UUID recordedBy;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_webhook_payload", columnDefinition = "jsonb")
    private String rawWebhookPayload;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "captured_at") private Instant capturedAt;
    @Column(name = "refund_requested_at") private Instant refundRequestedAt;
    @Column(name = "refunded_at") private Instant refundedAt;

    @Version
    private long version;

    protected Payment() {
        // for JPA
    }

    private Payment(UUID orderId, String provider, String providerOrderId, long amountPaise, PaymentAttemptStatus status, Instant now) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.provider = provider;
        this.providerOrderId = providerOrderId;
        this.amountPaise = amountPaise;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A checkout the customer is about to pay through the gateway. */
    public static Payment online(UUID orderId, String provider, String providerOrderId, long amountPaise, Instant now) {
        return new Payment(orderId, provider, providerOrderId, amountPaise, PaymentAttemptStatus.CREATED, now);
    }

    /** Staff took the money in person (cash, UPI to the restaurant, card machine). */
    public static Payment atCounter(UUID orderId, long amountPaise, CounterMethod method, UUID recordedBy, Instant now) {
        Payment payment = new Payment(orderId, COUNTER, null, amountPaise, PaymentAttemptStatus.CAPTURED, now);
        payment.counterMethod = method;
        payment.recordedBy = recordedBy;
        payment.capturedAt = now;
        return payment;
    }

    public boolean isCounter() {
        return COUNTER.equals(provider);
    }

    public boolean isSettled() {
        return status != PaymentAttemptStatus.CREATED && status != PaymentAttemptStatus.FAILED;
    }

    public void markCaptured(String providerPaymentId, Instant now) {
        this.providerPaymentId = providerPaymentId;
        this.status = PaymentAttemptStatus.CAPTURED;
        this.failureReason = null;
        this.capturedAt = now;
        this.updatedAt = now;
    }

    public void markFailed(String providerPaymentId, String reason, Instant now) {
        if (providerPaymentId != null) {
            this.providerPaymentId = providerPaymentId;
        }
        this.status = PaymentAttemptStatus.FAILED;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 300));
        this.updatedAt = now;
    }

    public void requestRefund(Instant now) {
        this.status = PaymentAttemptStatus.REFUND_REQUESTED;
        this.refundRequestedAt = now;
        this.updatedAt = now;
    }

    public void refundAccepted(String refundId, Instant now) {
        this.providerRefundId = refundId;
        this.status = PaymentAttemptStatus.REFUND_PENDING;
        this.updatedAt = now;
    }

    public void markRefunded(String refundId, Instant now) {
        if (refundId != null) {
            this.providerRefundId = refundId;
        }
        this.status = PaymentAttemptStatus.REFUNDED;
        this.refundedAt = now;
        this.updatedAt = now;
    }

    public void recordWebhook(String payload, Instant now) {
        this.rawWebhookPayload = payload;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getProvider() { return provider; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getProviderRefundId() { return providerRefundId; }
    public long getAmountPaise() { return amountPaise; }
    public String getCurrency() { return currency; }
    public PaymentAttemptStatus getStatus() { return status; }
    public CounterMethod getCounterMethod() { return counterMethod; }
    public UUID getRecordedBy() { return recordedBy; }
    public String getFailureReason() { return failureReason; }
    public String getRawWebhookPayload() { return rawWebhookPayload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getRefundRequestedAt() { return refundRequestedAt; }
    public Instant getRefundedAt() { return refundedAt; }
}
