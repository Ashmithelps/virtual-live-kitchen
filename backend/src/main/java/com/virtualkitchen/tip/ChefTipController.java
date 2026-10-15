package com.virtualkitchen.tip;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.tip.dto.ChefTipsPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('CHEF')")
public class ChefTipController {

    private final TipService service;

    public ChefTipController(TipService service) {
        this.service = service;
    }

    /** "My tips": received tips (paid or transferred), newest first. */
    @GetMapping("/api/chef/tips")
    public ChefTipsPage tips(@AuthenticationPrincipal AuthenticatedUser chef,
                             @RequestParam(defaultValue = "0") @Min(0) int page,
                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.chefTips(chef.id(), page, size);
    }
}
