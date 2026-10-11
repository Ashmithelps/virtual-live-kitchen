package com.virtualkitchen.order;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.common.web.PageResponse;
import com.virtualkitchen.order.dto.CancelOrderRequest;
import com.virtualkitchen.order.dto.ConfirmPaymentRequest;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.PaymentCheckoutResponse;
import com.virtualkitchen.order.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerOrderController {

    private final OrderService orderService;

    public CustomerOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** ONLINE orders come back with paymentStatus PENDING; then call POST .../payment to pay. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@AuthenticationPrincipal AuthenticatedUser customer,
                               @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(customer.id(), request);
    }

    /**
     * Starts (or resumes) paying: prepaid orders while PLACED, pay-at-table orders any time.
     * Returns what the browser needs to open the gateway's checkout. Amount is computed server-side.
     */
    @PostMapping("/{orderId}/payment")
    public PaymentCheckoutResponse startPayment(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID orderId) {
        return orderService.startPayment(customer.id(), orderId);
    }

    /** After checkout: verified with the gateway (signature + its own record) before the order is marked paid. */
    @PostMapping("/{orderId}/payment/confirm")
    public OrderResponse confirmPayment(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID orderId,
                                        @Valid @RequestBody ConfirmPaymentRequest request) {
        return orderService.confirmPayment(customer.id(), orderId, request);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID orderId,
                                @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancel(customer.id(), orderId, request == null ? null : request.reason());
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal AuthenticatedUser customer,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return orderService.list(customer.id(), page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser customer, @PathVariable UUID orderId) {
        return orderService.get(customer.id(), orderId);
    }
}
