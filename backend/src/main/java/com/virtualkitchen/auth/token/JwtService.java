package com.virtualkitchen.auth.token;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.auth.user.Role;
import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Issues and verifies short-lived HS256 access tokens. */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_STATUS = "status";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(secret);
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public IssuedAccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_STATUS, user.getStatus().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedAccessToken(token, expiresAt);
    }

    /** @throws InvalidTokenException if the token is expired, tampered with, or malformed */
    public AuthenticatedUser verify(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    UserStatus.valueOf(claims.get(CLAIM_STATUS, String.class)));
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Access token has expired");
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Access token is invalid");
        }
    }

    public record IssuedAccessToken(String token, Instant expiresAt) {
    }
}
