package com.virtualkitchen.admin;

import com.virtualkitchen.auth.dto.UserResponse;
import com.virtualkitchen.auth.user.Role;
import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserAccountService;
import com.virtualkitchen.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only user directory for admins (newest first, optionally by role). */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserListController {

    private final UserAccountService users;

    public AdminUserListController(UserAccountService users) {
        this.users = users;
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(required = false) Role role,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        Page<User> result = users.listUsers(role, page, size);
        return PageResponse.of(result.getContent().stream().map(UserResponse::from).toList(), page, size, result.getTotalElements());
    }
}
