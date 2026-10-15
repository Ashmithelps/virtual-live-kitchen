package com.virtualkitchen.tip;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.tip.dto.FollowStatusResponse;
import com.virtualkitchen.tip.dto.FollowedChefResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasRole('CUSTOMER')")
public class ChefFollowController {

    private final ChefFollowService service;

    public ChefFollowController(ChefFollowService service) {
        this.service = service;
    }

    @GetMapping("/api/chefs/{chefId}/follow")
    public FollowStatusResponse status(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID chefId) {
        return new FollowStatusResponse(service.follows(customer.id(), chefId));
    }

    @PutMapping("/api/chefs/{chefId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID chefId) {
        service.follow(customer.id(), chefId);
    }

    @DeleteMapping("/api/chefs/{chefId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID chefId) {
        service.unfollow(customer.id(), chefId);
    }

    @GetMapping("/api/me/followed-chefs")
    public List<FollowedChefResponse> followed(@AuthenticationPrincipal AuthenticatedUser customer,
                                               @RequestParam(required = false) UUID restaurantId) {
        return service.followed(customer.id(), restaurantId);
    }
}
