package com.virtualkitchen.realtime;

import com.virtualkitchen.auth.token.InvalidTokenException;
import com.virtualkitchen.auth.token.JwtService;
import com.virtualkitchen.order.guest.GuestOrderService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Security for every inbound STOMP frame:
 * <ul>
 *   <li>CONNECT must carry {@code Authorization: Bearer <access token>} or a guest's
 *       {@code X-Guest-Order-Token}; the resulting principal is fixed for the connection.</li>
 *   <li>SUBSCRIBE is allowed only to destinations {@link RealtimeAccess} approves.</li>
 *   <li>SEND is always refused: clients never publish, the server does (otherwise anyone could
 *       broadcast fake order events to a topic through the simple broker).</li>
 * </ul>
 * A refusal throws, which makes Spring send a STOMP ERROR frame and close the connection.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    static final String GUEST_TOKEN_HEADER = "X-Guest-Order-Token";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final GuestOrderService guestOrders;
    private final RealtimeAccess access;

    public StompAuthInterceptor(JwtService jwtService, GuestOrderService guestOrders, RealtimeAccess access) {
        this.jwtService = jwtService;
        this.guestOrders = guestOrders;
        this.access = access;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;
        }
        StompCommand command = stomp.getCommand();
        switch (command) {
            case CONNECT, STOMP -> stomp.setUser(authenticate(stomp));
            case SUBSCRIBE -> {
                if (!(stomp.getUser() instanceof RealtimePrincipal principal)
                        || !access.canSubscribe(principal, stomp.getDestination())) {
                    throw new MessageDeliveryException("Access denied to " + stomp.getDestination());
                }
            }
            case SEND -> throw new MessageDeliveryException("Access denied: clients cannot send messages");
            default -> {
                // UNSUBSCRIBE, DISCONNECT, ACK… need no checks.
            }
        }
        return message;
    }

    private RealtimePrincipal authenticate(StompHeaderAccessor stomp) {
        String authorization = stomp.getFirstNativeHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            try {
                return new RealtimePrincipal.User(jwtService.verify(authorization.substring(BEARER.length()).strip()));
            } catch (InvalidTokenException e) {
                throw new MessageDeliveryException("Unauthorized: " + e.getMessage());
            }
        }
        String guestToken = stomp.getFirstNativeHeader(GUEST_TOKEN_HEADER);
        if (guestToken != null) {
            return guestOrders.verify(guestToken)
                    .map(g -> (RealtimePrincipal) new RealtimePrincipal.Guest(g.orderId(), g.customerId()))
                    .orElseThrow(() -> new MessageDeliveryException("Unauthorized: guest token is invalid or expired"));
        }
        throw new MessageDeliveryException("Unauthorized: send Authorization: Bearer <token> in the CONNECT frame");
    }
}
