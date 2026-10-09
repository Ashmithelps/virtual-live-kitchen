package com.virtualkitchen.kitchen.chef;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.kitchen.chef.dto.ChefResponse;
import com.virtualkitchen.kitchen.chef.dto.CreateChefRequest;
import com.virtualkitchen.kitchen.chef.dto.UpdateChefRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/restaurants/{restaurantId}/chefs")
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class OwnerChefController {

    private final ChefService chefService;

    public OwnerChefController(ChefService chefService) {
        this.chefService = chefService;
    }

    @GetMapping
    public List<ChefResponse> list(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        return chefService.list(owner.id(), restaurantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChefResponse create(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                               @Valid @RequestBody CreateChefRequest request) {
        return chefService.create(owner.id(), restaurantId, request);
    }

    @GetMapping("/{chefId}")
    public ChefResponse get(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                            @PathVariable UUID chefId) {
        return chefService.get(owner.id(), restaurantId, chefId);
    }

    @PutMapping("/{chefId}")
    public ChefResponse update(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                               @PathVariable UUID chefId, @Valid @RequestBody UpdateChefRequest request) {
        return chefService.update(owner.id(), restaurantId, chefId, request);
    }

    @DeleteMapping("/{chefId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                       @PathVariable UUID chefId) {
        chefService.remove(owner.id(), restaurantId, chefId);
    }
}
