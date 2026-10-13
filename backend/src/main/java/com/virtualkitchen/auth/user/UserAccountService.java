package com.virtualkitchen.auth.user;

import com.virtualkitchen.auth.token.RefreshTokenService;
import com.virtualkitchen.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Public API of the auth module for reading and managing user accounts. */
@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JdbcTemplate jdbc;

    public UserAccountService(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              RefreshTokenService refreshTokenService,
                              JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.jdbc = jdbc;
    }

    /** Chefs never self-register; their restaurant owner creates the account. */
    @Transactional
    public User createChefUser(String name, String phone, String initialPassword) {
        if (userRepository.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "Phone number is already registered");
        }
        return userRepository.saveAndFlush(new User(name.strip(), phone, null,
                passwordEncoder.encode(initialPassword), Role.CHEF, UserStatus.ACTIVE));
    }

    /**
     * Guest checkout: returns the CUSTOMER with this phone, creating a passwordless one if needed.
     * The insert is ON CONFLICT DO NOTHING so two simultaneous guest orders for a new phone end up
     * with the same user instead of a unique-constraint failure. Staff phones are refused.
     */
    @Transactional
    public User findOrCreateGuestCustomer(String name, String phone) {
        jdbc.update("""
                INSERT INTO users (id, name, phone, email, password_hash, role, status, created_at)
                VALUES (?, ?, ?, NULL, NULL, 'CUSTOMER', 'ACTIVE', now())
                ON CONFLICT (phone) DO NOTHING
                """, UUID.randomUUID(), name.strip(), phone);
        User user = userRepository.findByPhone(phone).orElseThrow();
        if (user.getRole() != Role.CUSTOMER || user.isDisabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "This phone number belongs to a staff account; please log in instead");
        }
        return user;
    }

    /** Blocks login and refresh, and revokes existing sessions (access tokens lapse within 15 min). */
    @Transactional
    public void disable(UUID userId) {
        getById(userId).disable();
        refreshTokenService.revokeAllForUser(userId);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<User> listUsers(Role role, int page, int size) {
        return userRepository.search(role, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<User> findPendingOwners() {
        return userRepository.findByRoleAndStatusOrderByCreatedAtAsc(Role.RESTAURANT_OWNER, UserStatus.PENDING_APPROVAL);
    }

    @Transactional
    public User approveOwner(UUID userId) {
        User user = getById(userId);
        if (user.getRole() != Role.RESTAURANT_OWNER) {
            throw new ApiException(HttpStatus.CONFLICT, "User is not a restaurant owner");
        }
        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.CONFLICT, "Owner is not pending approval");
        }
        user.activate();
        return user;
    }
}
