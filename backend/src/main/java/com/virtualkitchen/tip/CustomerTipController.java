package com.virtualkitchen.tip;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.order.dto.ConfirmPaymentRequest;
import com.virtualkitchen.tip.dto.OrderTipsResponse;
import com.virtualkitchen.tip.dto.TipCheckoutResponse;
import com.virtualkitchen.tip.dto.TipRequest;
import com.virtualkitchen.tip.dto.TipResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Tipping a chef from one of the customer's orders. */
@RestController
@RequestMapping("/api/orders/{orderId}/tips")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerTipController {

    private final TipService service;

    public CustomerTipController(TipService service) {
        this.service = service;
    }

    @GetMapping
    public OrderTipsResponse options(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID orderId) {
        return service.options(customer.id(), orderId);
    }

    @PostMapping
    public TipCheckoutResponse start(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID orderId,
                                     @Valid @RequestBody TipRequest request) {
        return service.start(customer.id(), orderId, request);
    }

    @PostMapping("/{tipId}/confirm")
    public TipResponse confirm(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID orderId, @PathVariable UUID tipId,
                               @Valid @RequestBody ConfirmPaymentRequest request) {
        return service.confirm(customer.id(), orderId, tipId, request);
    }
}
