package com.virtualkitchen.auth.token;

import com.virtualkitchen.common.crypto.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Opaque, single-use refresh tokens. Only a SHA-256 hash is persisted; the raw value is returned
 * to the client once. Presenting an already-rotated token is treated as theft and revokes every
 * active refresh token of that user.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issue(UUID userId) {
        return create(userId, clock.instant()).issued();
    }

    /**
     * Consumes {@code rawToken} and issues its successor. {@code noRollbackFor} keeps the
     * reuse-detection revocation committed even though the caller gets a 401.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));

        if (current.isRevoked()) {
            int revoked = repository.revokeAllForUser(current.getUserId(), now);
            log.warn("Reuse of revoked refresh token {} for user {}; revoked {} active token(s)",
                    current.getId(), current.getUserId(), revoked);
            throw new InvalidTokenException("Refresh token is invalid");
        }
        if (current.isExpired(now)) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        Created successor = create(current.getUserId(), now);
        current.rotateTo(successor.entity(), now);
        return new Rotation(current.getUserId(), successor.issued());
    }

    /** Idempotent: unknown or already-revoked tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHashForUpdate(hash(rawToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private Created create(UUID userId, Instant now) {
        String raw = SecureTokens.random(TOKEN_BYTES);
        RefreshToken entity = repository.save(
                new RefreshToken(userId, hash(raw), now, now.plus(properties.refreshTokenTtl())));
        return new Created(entity, new IssuedRefreshToken(raw, entity.getExpiresAt()));
    }

    static String hash(String rawToken) {
        return SecureTokens.sha256Hex(rawToken);
    }

    public record IssuedRefreshToken(String token, Instant expiresAt) {
    }

    public record Rotation(UUID userId, IssuedRefreshToken refreshToken) {
    }

    private record Created(RefreshToken entity, IssuedRefreshToken issued) {
    }
}
