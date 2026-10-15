package com.virtualkitchen.tip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a chef's tips go: their Razorpay Route linked account. Bank account, IFSC and PAN live only at the gateway;
 * we keep the account id, its status and masked fragments for display.
 */
@Entity
@Table(name = "chef_payout_accounts")
public class ChefPayoutAccount {

    @Id
    @Column(name = "chef_id")
    private UUID chefId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_account_id", nullable = false, length = 64)
    private String providerAccountId;

    @Column(name = "provider_product_id", length = 64)
    private String providerProductId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "legal_name", nullable = false, length = 120)
    private String legalName;

    @Column(name = "bank_account_last4", nullable = false, length = 4)
    private String bankAccountLast4;

    @Column(name = "bank_code", nullable = false, length = 4)
    private String bankCode;

    @Column(name = "pan_last4", nullable = false, length = 4)
    private String panLast4;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    protected ChefPayoutAccount() {
    }

    ChefPayoutAccount(UUID chefId, String provider, String accountId, String legalName, String accountNumber, String ifsc, String pan,
                      Instant now) {
        this.chefId = chefId;
        this.provider = provider;
        this.providerAccountId = accountId;
        this.legalName = legalName;
        this.panLast4 = pan.substring(pan.length() - 4);
        this.createdAt = now;
        this.status = PayoutStatus.PENDING_KYC;
        bank(accountNumber, ifsc, now);
    }

    void bank(String accountNumber, String ifsc, Instant now) {
        this.bankAccountLast4 = accountNumber.substring(accountNumber.length() - 4);
        this.bankCode = ifsc.substring(0, 4);
        this.updatedAt = now;
    }

    void sync(String productId, boolean active, String providerStatus, Instant now) {
        this.providerProductId = productId;
        this.providerStatus = providerStatus;
        if (active && status != PayoutStatus.ACTIVE) {
            this.activatedAt = now;
        }
        this.status = active ? PayoutStatus.ACTIVE : PayoutStatus.PENDING_KYC;
        this.updatedAt = now;
    }

    public UUID getChefId() { return chefId; }
    public String getProvider() { return provider; }
    public String getProviderAccountId() { return providerAccountId; }
    public String getProviderProductId() { return providerProductId; }
    public PayoutStatus getStatus() { return status; }
    public String getProviderStatus() { return providerStatus; }
    public String getLegalName() { return legalName; }
    public String getBankAccountLast4() { return bankAccountLast4; }
    public String getBankCode() { return bankCode; }
    public String getPanLast4() { return panLast4; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getActivatedAt() { return activatedAt; }
}
