package com.virtualkitchen.auth;

import com.virtualkitchen.auth.dto.UserResponse;
import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.auth.user.UserAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final UserAccountService userAccountService;

    public MeController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    /** Available to any authenticated user, including owners still pending approval. */
    @GetMapping("/api/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(userAccountService.getById(principal.id()));
    }
}
