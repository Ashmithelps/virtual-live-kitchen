package com.virtualkitchen.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived signed tokens for narrow purposes (table sessions, guest order access). Each
 * purpose signs with its own key derived from JWT_SECRET, so a token for one purpose is never
 * accepted for another, nor as a login access token.
 */
@Component
public class ScopedTokens {

    private final byte[] secret;
    private final String issuer;
    private final Clock clock;
    private final Map<String, SecretKey> keys = new ConcurrentHashMap<>();

    public ScopedTokens(JwtProperties properties, Clock clock) {
        this.secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        this.issuer = properties.issuer();
        this.clock = clock;
    }

    public Issued issue(String purpose, String subject, Map<String, Object> claims, Duration ttl) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(issuer)
                .audience().add(purpose).and()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key(purpose), Jwts.SIG.HS256)
                .compact();
        return new Issued(token, expiresAt);
    }

    /** @return the claims, or empty if the token is malformed, tampered with, expired, or for another purpose */
    public Optional<Claims> verify(String purpose, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key(purpose))
                    .requireIssuer(issuer)
                    .requireAudience(purpose)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (ExpiredJwtException e) {
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private SecretKey key(String purpose) {
        return keys.computeIfAbsent(purpose, p -> {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret, "HmacSHA256"));
                byte[] derived = mac.doFinal(("scoped-token:" + p).getBytes(StandardCharsets.UTF_8));
                return new SecretKeySpec(derived, "HmacSHA256");
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("HmacSHA256 unavailable", e);
            }
        });
    }

    public record Issued(String token, Instant expiresAt) {

        @Override
        public String toString() {
            return "Issued[token=***, expiresAt=" + expiresAt + "]";
        }
    }
}
