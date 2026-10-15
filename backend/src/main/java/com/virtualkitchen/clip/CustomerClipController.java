package com.virtualkitchen.clip;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.clip.dto.ClipPlaybackResponse;
import com.virtualkitchen.clip.dto.ClipShareResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The customer's own clip: watch/download links and the public share link. */
@RestController
@RequestMapping("/api/orders/{orderId}/clip")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerClipController {

    private final ClipPlaybackService service;

    public CustomerClipController(ClipPlaybackService service) {
        this.service = service;
    }

    @GetMapping
    public ClipPlaybackResponse playback(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID orderId) {
        return service.forCustomer(customer.id(), orderId);
    }

    /** Returns the active share link, creating one if needed. */
    @PostMapping("/share")
    public ClipShareResponse share(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID orderId) {
        return service.share(customer.id(), orderId);
    }

    @DeleteMapping("/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID orderId) {
        service.revoke(customer.id(), orderId);
    }
}
