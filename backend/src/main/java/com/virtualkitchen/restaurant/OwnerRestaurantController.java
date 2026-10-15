package com.virtualkitchen.restaurant;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.restaurant.dto.ComplianceRequest;
import com.virtualkitchen.restaurant.dto.ComplianceResponse;
import com.virtualkitchen.restaurant.dto.RestaurantRequest;
import com.virtualkitchen.restaurant.dto.RestaurantResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/owner/restaurants")
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class OwnerRestaurantController {

    private final RestaurantService restaurantService;

    public OwnerRestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    /** New restaurants start PENDING_APPROVAL and are hidden from customers until an admin approves them. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantResponse create(@AuthenticationPrincipal AuthenticatedUser owner,
                                     @Valid @RequestBody RestaurantRequest request) {
        return restaurantService.create(owner.id(), request);
    }

    @GetMapping
    public List<RestaurantResponse> list(@AuthenticationPrincipal AuthenticatedUser owner) {
        return restaurantService.listOwned(owner.id());
    }

    @GetMapping("/{restaurantId}")
    public RestaurantResponse get(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        return restaurantService.getOwned(owner.id(), restaurantId);
    }

    @PutMapping("/{restaurantId}")
    public RestaurantResponse update(@AuthenticationPrincipal AuthenticatedUser owner,
                                     @PathVariable UUID restaurantId,
                                     @Valid @RequestBody RestaurantRequest request) {
        return restaurantService.update(owner.id(), restaurantId, request);
    }

    /** FSSAI licence and tax details (seen by admins; the GST details go on invoices). */
    @GetMapping("/{restaurantId}/compliance")
    public ComplianceResponse compliance(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        return restaurantService.compliance(owner.id(), restaurantId);
    }

    @PutMapping("/{restaurantId}/compliance")
    public ComplianceResponse updateCompliance(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                               @Valid @RequestBody ComplianceRequest request) {
        return restaurantService.updateCompliance(owner.id(), restaurantId, request);
    }
}
