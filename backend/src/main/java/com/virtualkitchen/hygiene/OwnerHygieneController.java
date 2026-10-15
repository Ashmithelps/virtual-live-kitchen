package com.virtualkitchen.hygiene;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.hygiene.dto.HygieneDisputeRequest;
import com.virtualkitchen.hygiene.dto.HygieneDisputeResponse;
import com.virtualkitchen.hygiene.dto.HygieneOverviewResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/owner/restaurants/{restaurantId}/hygiene")
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class OwnerHygieneController {

    private final HygieneService service;

    public OwnerHygieneController(HygieneService service) {
        this.service = service;
    }

    @GetMapping
    public HygieneOverviewResponse overview(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        return service.overview(owner.id(), restaurantId);
    }

    /** Asks an admin to review a result the owner believes is wrong. */
    @PostMapping("/results/{resultId}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    public HygieneDisputeResponse dispute(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                          @PathVariable UUID resultId, @Valid @RequestBody HygieneDisputeRequest request) {
        return service.dispute(owner.id(), restaurantId, resultId, request.reason());
    }
}
