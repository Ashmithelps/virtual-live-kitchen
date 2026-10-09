package com.virtualkitchen.order;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.OrderStatusChangeRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Kitchen screen: chefs see their restaurant's active orders and may only move ACCEPTED → PREPARING → READY. */
@RestController
@RequestMapping("/api/chef/orders")
@PreAuthorize("hasRole('CHEF')")
public class ChefOrderController {

    private final RestaurantOrderService service;

    public ChefOrderController(RestaurantOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<OrderResponse> list(@AuthenticationPrincipal AuthenticatedUser chef,
                                    @RequestParam(required = false) OrderStatus status) {
        return service.listActiveForChef(chef.id(), status);
    }

    /** For PREPARING, send {@code stationId}; the chef is recorded as the preparer. */
    @PatchMapping("/{orderId}/status")
    public OrderResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser chef, @PathVariable UUID orderId,
                                      @Valid @RequestBody OrderStatusChangeRequest request) {
        return service.changeStatusAsChef(chef.id(), orderId, request);
    }
}
