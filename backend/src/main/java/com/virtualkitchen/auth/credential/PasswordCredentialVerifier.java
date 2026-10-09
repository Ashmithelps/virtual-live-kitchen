package com.virtualkitchen.auth.credential;

import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PasswordCredentialVerifier implements CredentialVerifier {

    private static final String MESSAGE = "Invalid phone number or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    /** Compared against when the phone is unknown, so response time doesn't reveal registered phones. */
    private final String dummyHash;

    public PasswordCredentialVerifier(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-placeholder");
    }

    @Override
    public boolean supports(LoginCredentials credentials) {
        return credentials instanceof PasswordCredentials;
    }

    @Override
    public User verify(LoginCredentials credentials) {
        PasswordCredentials password = (PasswordCredentials) credentials;
        Optional<User> user = userRepository.findByPhone(password.phone());
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(password.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new InvalidCredentialsException(MESSAGE);
        }
        return user.get();
    }
}
