package com.virtualkitchen.kitchen.chef;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.kitchen.chef.dto.ChefSelfResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('CHEF')")
public class ChefSelfController {

    private final ChefService chefService;

    public ChefSelfController(ChefService chefService) {
        this.chefService = chefService;
    }

    @GetMapping("/api/chef/me")
    public ChefSelfResponse me(@AuthenticationPrincipal AuthenticatedUser chef) {
        return chefService.self(chef.id());
    }
}
