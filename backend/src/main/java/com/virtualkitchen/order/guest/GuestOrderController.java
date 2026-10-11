package com.virtualkitchen.order.guest;

import com.virtualkitchen.order.dto.CancelOrderRequest;
import com.virtualkitchen.order.dto.ConfirmPaymentRequest;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.PaymentCheckoutResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** No login required. Follow-up calls authenticate with the order-scoped guest token. */
@RestController
@RequestMapping("/api/guest/orders")
public class GuestOrderController {

    static final String TOKEN_HEADER = "X-Guest-Order-Token";

    private final GuestOrderService service;

    public GuestOrderController(GuestOrderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuestOrderResponse place(@Valid @RequestBody GuestOrderRequest request) {
        return service.place(request);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId,
                             @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return service.get(orderId, token);
    }

    @PostMapping("/{orderId}/payment")
    public PaymentCheckoutResponse startPayment(@PathVariable UUID orderId,
                                                @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return service.startPayment(orderId, token);
    }

    @PostMapping("/{orderId}/payment/confirm")
    public OrderResponse confirmPayment(@PathVariable UUID orderId,
                                        @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                        @Valid @RequestBody ConfirmPaymentRequest request) {
        return service.confirmPayment(orderId, token, request);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable UUID orderId,
                                @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return service.cancel(orderId, token, request == null ? null : request.reason());
    }
}
