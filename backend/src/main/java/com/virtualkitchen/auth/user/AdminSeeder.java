package com.virtualkitchen.auth.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the platform ADMIN from ADMIN_PHONE / ADMIN_PASSWORD on startup. Never overwrites an
 * existing account, so rotating ADMIN_PASSWORD later does not change the stored password.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(AdminProperties properties, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String phone = properties.phone();
        if (phone == null || phone.isBlank()) {
            log.warn("ADMIN_PHONE not set; skipping admin seeding");
            return;
        }
        if (!phone.matches(PhoneNumbers.E164_REGEX)) {
            throw new IllegalStateException("ADMIN_PHONE " + PhoneNumbers.E164_MESSAGE);
        }
        var existing = userRepository.findByPhone(phone);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                log.warn("ADMIN_PHONE {} belongs to a {} account; not seeding admin", phone, existing.get().getRole());
            }
            return;
        }
        String password = properties.password();
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must be " + MIN_PASSWORD_LENGTH + "–72 characters");
        }
        String name = properties.name() == null || properties.name().isBlank() ? "Platform Admin" : properties.name();
        userRepository.save(new User(name, phone, null, passwordEncoder.encode(password), Role.ADMIN, UserStatus.ACTIVE));
        log.info("Seeded ADMIN account for {}", phone);
    }
}
