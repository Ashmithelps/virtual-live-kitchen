package com.virtualkitchen.clip;

import com.virtualkitchen.clip.dto.ClipPlaybackResponse;
import com.virtualkitchen.clip.dto.ClipShareResponse;
import com.virtualkitchen.order.guest.GuestOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Same as {@link CustomerClipController} for guest orders (authenticated by X-Guest-Order-Token). */
@RestController
@RequestMapping("/api/guest/orders/{orderId}/clip")
public class GuestClipController {

    private static final String TOKEN_HEADER = "X-Guest-Order-Token";

    private final ClipPlaybackService service;
    private final GuestOrderService guests;

    public GuestClipController(ClipPlaybackService service, GuestOrderService guests) {
        this.service = service;
        this.guests = guests;
    }

    @GetMapping
    public ClipPlaybackResponse playback(@PathVariable UUID orderId, @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return service.forCustomer(guests.requireCustomer(orderId, token), orderId);
    }

    @PostMapping("/share")
    public ClipShareResponse share(@PathVariable UUID orderId, @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return service.share(guests.requireCustomer(orderId, token), orderId);
    }

    @DeleteMapping("/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID orderId, @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        service.revoke(guests.requireCustomer(orderId, token), orderId);
    }
}
