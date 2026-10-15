package com.virtualkitchen.clip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A public link to one clip (/c/{token}), created by the order's customer and revocable by them. */
@Entity
@Table(name = "clip_shares")
public class ClipShare {

    @Id
    private UUID id;

    @Column(name = "clip_id", nullable = false, updatable = false)
    private UUID clipId;

    @Column(nullable = false, updatable = false, length = 64)
    private String token;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ClipShare() {
    }

    ClipShare(UUID clipId, String token, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.clipId = clipId;
        this.token = token;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    void revoke(Instant now) {
        this.revokedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getClipId() { return clipId; }
    public String getToken() { return token; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
