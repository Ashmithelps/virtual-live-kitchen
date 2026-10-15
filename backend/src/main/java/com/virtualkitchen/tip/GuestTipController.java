package com.virtualkitchen.tip;

import com.virtualkitchen.order.dto.ConfirmPaymentRequest;
import com.virtualkitchen.order.guest.GuestOrderService;
import com.virtualkitchen.tip.dto.OrderTipsResponse;
import com.virtualkitchen.tip.dto.TipCheckoutResponse;
import com.virtualkitchen.tip.dto.TipRequest;
import com.virtualkitchen.tip.dto.TipResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Same as {@link CustomerTipController} for guest orders (X-Guest-Order-Token). */
@RestController
@RequestMapping("/api/guest/orders/{orderId}/tips")
public class GuestTipController {

    private static final String TOKEN_HEADER = "X-Guest-Order-Token";

    private final TipService service;
    private final GuestOrderService guests;

    public GuestTipController(TipService service, GuestOrderService guests) {
        this.service = service;
        this.guests = guests;
    }

    @GetMapping
    public OrderTipsResponse options(@PathVariable UUID orderId, @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return service.options(guests.requireCustomer(orderId, token), orderId);
    }

    @PostMapping
    public TipCheckoutResponse start(@PathVariable UUID orderId, @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                     @Valid @RequestBody TipRequest request) {
        return service.start(guests.requireCustomer(orderId, token), orderId, request);
    }

    @PostMapping("/{tipId}/confirm")
    public TipResponse confirm(@PathVariable UUID orderId, @PathVariable UUID tipId,
                               @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                               @Valid @RequestBody ConfirmPaymentRequest request) {
        return service.confirm(guests.requireCustomer(orderId, token), orderId, tipId, request);
    }
}
