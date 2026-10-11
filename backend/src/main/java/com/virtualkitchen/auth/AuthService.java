package com.virtualkitchen.auth;

import com.virtualkitchen.auth.credential.CredentialVerifier;
import com.virtualkitchen.auth.credential.InvalidCredentialsException;
import com.virtualkitchen.auth.credential.LoginCredentials;
import com.virtualkitchen.auth.ratelimit.LoginRateLimiter;
import com.virtualkitchen.auth.token.InvalidTokenException;
import com.virtualkitchen.auth.token.JwtService;
import com.virtualkitchen.auth.token.RefreshTokenService;
import com.virtualkitchen.auth.token.TokenPair;
import com.virtualkitchen.auth.user.Role;
import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserRepository;
import com.virtualkitchen.auth.user.UserStatus;
import com.virtualkitchen.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Orchestrates registration and sessions. How a user proves identity is delegated to
 * {@link CredentialVerifier}s, so new login methods (OTP) plug in without touching this class
 * or the controllers.
 */
@Service
public class AuthService {

    private static final Set<Role> SELF_REGISTRATION_ROLES = Set.of(Role.CUSTOMER, Role.RESTAURANT_OWNER);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final List<CredentialVerifier> verifiers;
    private final LoginRateLimiter loginRateLimiter;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       List<CredentialVerifier> verifiers,
                       LoginRateLimiter loginRateLimiter,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.verifiers = verifiers;
        this.loginRateLimiter = loginRateLimiter;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResult register(Registration registration) {
        Role role = registration.role() == null ? Role.CUSTOMER : registration.role();
        if (!SELF_REGISTRATION_ROLES.contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only CUSTOMER or RESTAURANT_OWNER accounts can self-register");
        }
        var existing = userRepository.findByPhone(registration.phone());
        if (existing.isPresent()) {
            User user = existing.get();
            // A guest who ordered with this phone is now registering: claim the passwordless account.
            // Until OTP verification exists, this trusts the phone number like every other auth path.
            if (role == Role.CUSTOMER && user.getRole() == Role.CUSTOMER && !user.hasPassword() && !user.isDisabled()) {
                user.claim(registration.name().strip(), blankToNull(registration.email()),
                        passwordEncoder.encode(registration.password()));
                return new AuthResult(user, issueTokens(user));
            }
            throw new ApiException(HttpStatus.CONFLICT, "Phone number is already registered");
        }
        UserStatus status = role == Role.RESTAURANT_OWNER ? UserStatus.PENDING_APPROVAL : UserStatus.ACTIVE;
        User user = userRepository.saveAndFlush(new User(
                registration.name().strip(),
                registration.phone(),
                blankToNull(registration.email()),
                passwordEncoder.encode(registration.password()),
                role,
                status));
        return new AuthResult(user, issueTokens(user));
    }

    public AuthResult login(LoginCredentials credentials) {
        loginRateLimiter.consume(credentials.phone());
        CredentialVerifier verifier = verifiers.stream()
                .filter(v -> v.supports(credentials))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No verifier for " + credentials.getClass().getSimpleName()));
        User user = verifier.verify(credentials);
        if (user.isDisabled()) {
            throw new InvalidCredentialsException("Account is disabled");
        }
        loginRateLimiter.reset(credentials.phone());
        return new AuthResult(user, issueTokens(user));
    }

    /** Rotates the refresh token and re-reads the user, so role/status changes take effect. */
    public AuthResult refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        User user = userRepository.findById(rotation.userId())
                .filter(u -> !u.isDisabled())
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));
        JwtService.IssuedAccessToken access = jwtService.issue(user);
        return new AuthResult(user, new TokenPair(access.token(), access.expiresAt(),
                rotation.refreshToken().token(), rotation.refreshToken().expiresAt()));
    }

    /** Revokes the refresh token. The access token stays valid until it expires (max 15 min). */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private TokenPair issueTokens(User user) {
        JwtService.IssuedAccessToken access = jwtService.issue(user);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user.getId());
        return new TokenPair(access.token(), access.expiresAt(), refresh.token(), refresh.expiresAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record Registration(String name, String phone, String email, String password, Role role) {

        @Override
        public String toString() {
            return "Registration[phone=" + phone + ", role=" + role + "]";
        }
    }

    public record AuthResult(User user, TokenPair tokens) {
    }
}
