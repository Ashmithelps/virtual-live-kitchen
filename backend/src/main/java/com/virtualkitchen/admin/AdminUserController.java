package com.virtualkitchen.admin;

import com.virtualkitchen.auth.dto.UserResponse;
import com.virtualkitchen.auth.user.UserAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/owners")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAccountService userAccountService;

    public AdminUserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/pending")
    public List<UserResponse> pendingOwners() {
        return userAccountService.findPendingOwners().stream().map(UserResponse::from).toList();
    }

    @PostMapping("/{userId}/approve")
    public UserResponse approve(@PathVariable UUID userId) {
        return UserResponse.from(userAccountService.approveOwner(userId));
    }
}
