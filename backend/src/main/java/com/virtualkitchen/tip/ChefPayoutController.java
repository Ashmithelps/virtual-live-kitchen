package com.virtualkitchen.tip;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.tip.dto.PayoutAccountRequest;
import com.virtualkitchen.tip.dto.PayoutAccountResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The chef's own payout set-up (where tips go). */
@RestController
@RequestMapping("/api/chef/payout-account")
@PreAuthorize("hasRole('CHEF')")
public class ChefPayoutController {

    private final PayoutAccountService service;

    public ChefPayoutController(PayoutAccountService service) {
        this.service = service;
    }

    @GetMapping
    public PayoutAccountResponse get(@AuthenticationPrincipal AuthenticatedUser chef) {
        return service.get(chef.id());
    }

    @PutMapping
    public PayoutAccountResponse submit(@AuthenticationPrincipal AuthenticatedUser chef, @Valid @RequestBody PayoutAccountRequest request) {
        return service.submit(chef.id(), request);
    }

    @PostMapping("/refresh")
    public PayoutAccountResponse refresh(@AuthenticationPrincipal AuthenticatedUser chef) {
        return service.refresh(chef.id());
    }
}
