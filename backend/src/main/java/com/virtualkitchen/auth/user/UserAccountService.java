package com.virtualkitchen.auth.user;

import com.virtualkitchen.auth.token.RefreshTokenService;
import com.virtualkitchen.common.error.ApiException;
import org.springframework.http.HttpStatus;
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

    public UserAccountService(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
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
