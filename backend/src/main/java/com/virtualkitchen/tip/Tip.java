package com.virtualkitchen.tip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One tip: a separate gateway payment whose chef share is transferred to the chef's linked account on capture. */
@Entity
@Table(name = "tips")
public class Tip {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "chef_id", nullable = false, updatable = false)
    private UUID chefId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TipSource source;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "platform_fee_paise", nullable = false, updatable = false)
    private long platformFeePaise;

    @Column(name = "chef_amount_paise", nullable = false, updatable = false)
    private long chefAmountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipStatus status;

    @Column(nullable = false, updatable = false, length = 20)
    private String provider;

    @Column(name = "provider_order_id", length = 64)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 64)
    private String providerPaymentId;

    @Column(name = "provider_transfer_id", length = 64)
    private String providerTransferId;

    @Column(name = "linked_account_id", nullable = false, updatable = false, length = 64)
    private String linkedAccountId;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "transferred_at") private Instant transferredAt;

    protected Tip() {
    }

    Tip(UUID orderId, UUID chefId, UUID customerId, TipSource source, long amountPaise, long platformFeePaise, String provider,
        String linkedAccountId, Instant now) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.chefId = chefId;
        this.customerId = customerId;
        this.source = source;
        this.amountPaise = amountPaise;
        this.platformFeePaise = platformFeePaise;
        this.chefAmountPaise = amountPaise - platformFeePaise;
        this.status = TipStatus.CREATED;
        this.provider = provider;
        this.linkedAccountId = linkedAccountId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void attachProviderOrder(String providerOrderId, Instant now) {
        this.providerOrderId = providerOrderId;
        this.updatedAt = now;
    }

    void markPaid(String paymentId, Instant now) {
        this.providerPaymentId = paymentId;
        this.status = TipStatus.PAID;
        this.failureReason = null;
        this.paidAt = now;
        this.updatedAt = now;
    }

    void markTransferred(String transferId, Instant now) {
        this.providerTransferId = transferId;
        this.status = TipStatus.TRANSFERRED;
        this.transferredAt = now;
        this.updatedAt = now;
    }

    void markFailed(String paymentId, String reason, Instant now) {
        if (paymentId != null) this.providerPaymentId = paymentId;
        this.status = TipStatus.FAILED;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(300, reason.length()));
        this.updatedAt = now;
    }

    /** A transfer the gateway couldn't complete: the money is held at the gateway and needs follow-up. */
    void noteTransferProblem(String reason, Instant now) {
        this.failureReason = reason == null ? "Transfer failed" : reason.substring(0, Math.min(300, reason.length()));
        this.updatedAt = now;
    }

    boolean isPaid() {
        return status == TipStatus.PAID || status == TipStatus.TRANSFERRED;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getChefId() { return chefId; }
    public UUID getCustomerId() { return customerId; }
    public TipSource getSource() { return source; }
    public long getAmountPaise() { return amountPaise; }
    public long getPlatformFeePaise() { return platformFeePaise; }
    public long getChefAmountPaise() { return chefAmountPaise; }
    public TipStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getLinkedAccountId() { return linkedAccountId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getTransferredAt() { return transferredAt; }
}
