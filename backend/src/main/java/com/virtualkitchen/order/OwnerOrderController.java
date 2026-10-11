package com.virtualkitchen.order;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.order.dto.CounterPaymentRequest;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.OrderStatusChangeRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/restaurants/{restaurantId}/orders")
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class OwnerOrderController {

    private final RestaurantOrderService service;
    private final OrderPaymentService payments;

    public OwnerOrderController(RestaurantOrderService service, OrderPaymentService payments) {
        this.service = service;
        this.payments = payments;
    }

    /** Active (non-terminal) orders, oldest first; {@code status} narrows to one status. */
    @GetMapping
    public List<OrderResponse> list(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                    @RequestParam(required = false) OrderStatus status) {
        return service.listActiveForOwner(owner.id(), restaurantId, status);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                             @PathVariable UUID orderId) {
        return service.getForOwner(owner.id(), restaurantId, orderId);
    }

    /** Pay-at-table bills not settled yet, including served orders (they're off the active list). */
    @GetMapping("/unpaid")
    public List<OrderResponse> unpaid(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        return payments.unpaidBills(owner.id(), restaurantId);
    }

    /** "Paid at counter": cash, UPI or card taken by staff. */
    @PostMapping("/{orderId}/payment/counter")
    public OrderResponse paidAtCounter(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                       @PathVariable UUID orderId, @Valid @RequestBody CounterPaymentRequest request) {
        return payments.markPaidAtCounter(owner.id(), restaurantId, orderId, request.method());
    }

    @PatchMapping("/{orderId}/status")
    public OrderResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                      @PathVariable UUID orderId, @Valid @RequestBody OrderStatusChangeRequest request) {
        return service.changeStatusAsOwner(owner.id(), restaurantId, orderId, request);
    }
}
