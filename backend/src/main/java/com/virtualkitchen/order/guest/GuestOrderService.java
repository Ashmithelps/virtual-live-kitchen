package com.virtualkitchen.order.guest;

import com.virtualkitchen.auth.token.ScopedTokens;
import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserAccountService;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.order.OrderService;
import com.virtualkitchen.order.dto.ConfirmPaymentRequest;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.PaymentCheckoutResponse;
import com.virtualkitchen.order.dto.PlaceOrderRequest;
import com.virtualkitchen.restaurant.OrderType;
import com.virtualkitchen.restaurant.table.TableSessions;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dine-in checkout with only name + phone. The order is owned by the CUSTOMER with that phone
 * (created passwordless if new), so it shows up in their history once they register or log in.
 *
 * <p>Because the phone isn't verified yet, the caller never receives login tokens for that user
 * (that would let anyone take over an account by typing its phone number). They get a token
 * scoped to the one order instead.
 */
@Service
public class GuestOrderService {

    private static final String PURPOSE = "guest-order";
    private static final String CLAIM_CUSTOMER = "cid";

    private final OrderService orderService;
    private final UserAccountService userAccountService;
    private final TableSessions tableSessions;
    private final ScopedTokens tokens;
    private final GuestOrderProperties properties;

    public GuestOrderService(OrderService orderService, UserAccountService userAccountService,
                             TableSessions tableSessions, ScopedTokens tokens, GuestOrderProperties properties) {
        this.orderService = orderService;
        this.userAccountService = userAccountService;
        this.tableSessions = tableSessions;
        this.tokens = tokens;
        this.properties = properties;
    }

    /** One transaction: if the order is invalid, a newly created guest user is rolled back too. */
    @Transactional
    public GuestOrderResponse place(GuestOrderRequest request) {
        UUID restaurantId = tableSessions.restaurantOf(request.tableSessionToken());
        User customer = userAccountService.findOrCreateGuestCustomer(request.name(), request.phone());
        OrderResponse order = orderService.place(customer.getId(), new PlaceOrderRequest(
                restaurantId, OrderType.DINE_IN, request.items(), request.tableSessionToken(), null,
                request.paymentMethod(), request.notes()));
        ScopedTokens.Issued token = tokens.issue(PURPOSE, order.id().toString(),
                Map.of(CLAIM_CUSTOMER, customer.getId().toString()), properties.tokenTtl());
        return new GuestOrderResponse(order, token.token(), token.expiresAt());
    }

    public OrderResponse get(UUID orderId, String guestToken) {
        return orderService.get(customerFor(orderId, guestToken), orderId);
    }

    public PaymentCheckoutResponse startPayment(UUID orderId, String guestToken) {
        return orderService.startPayment(customerFor(orderId, guestToken), orderId);
    }

    public OrderResponse confirmPayment(UUID orderId, String guestToken, ConfirmPaymentRequest request) {
        return orderService.confirmPayment(customerFor(orderId, guestToken), orderId, request);
    }

    public OrderResponse cancel(UUID orderId, String guestToken, String reason) {
        return orderService.cancel(customerFor(orderId, guestToken), orderId, reason);
    }

    /** The order and customer a guest token grants access to, if it's valid. */
    public Optional<GuestAccess> verify(String guestToken) {
        return tokens.verify(PURPOSE, guestToken).map(c -> new GuestAccess(
                UUID.fromString(c.getSubject()), UUID.fromString(c.get(CLAIM_CUSTOMER, String.class))));
    }

    public record GuestAccess(UUID orderId, UUID customerId) {
    }

    /** The customer a guest token stands for, if it's valid for this order; 401 otherwise. */
    public UUID requireCustomer(UUID orderId, String guestToken) {
        return customerFor(orderId, guestToken);
    }

    private UUID customerFor(UUID orderId, String guestToken) {
        Claims claims = tokens.verify(PURPOSE, guestToken)
                .filter(c -> orderId.toString().equals(c.getSubject()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Guest order token is missing, invalid or expired"));
        return UUID.fromString(claims.get(CLAIM_CUSTOMER, String.class));
    }
}
