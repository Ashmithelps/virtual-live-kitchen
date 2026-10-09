package com.virtualkitchen.kitchen.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One acceptance of a specific consent text. Never deleted; withdrawal only sets {@code withdrawnAt}. */
@Entity
@Table(name = "chef_consents")
public class ChefConsent {

    @Id
    private UUID id;

    @Column(name = "chef_id", nullable = false, updatable = false)
    private UUID chefId;

    @Column(name = "consent_version", nullable = false, updatable = false, length = 20)
    private String consentVersion;

    @Column(name = "consent_text_hash", nullable = false, updatable = false, length = 64)
    private String consentTextHash;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(nullable = false, updatable = false, length = 45)
    private String ip;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected ChefConsent() {
        // for JPA
    }

    public ChefConsent(UUID chefId, String consentVersion, String consentTextHash, Instant acceptedAt, String ip) {
        this.id = UUID.randomUUID();
        this.chefId = chefId;
        this.consentVersion = consentVersion;
        this.consentTextHash = consentTextHash;
        this.acceptedAt = acceptedAt;
        this.ip = ip;
    }

    public void withdraw(Instant now) {
        if (withdrawnAt == null) {
            withdrawnAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getChefId() { return chefId; }
    public String getConsentVersion() { return consentVersion; }
    public String getConsentTextHash() { return consentTextHash; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public String getIp() { return ip; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
}
