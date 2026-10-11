package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real SockJS + STOMP connections against the embedded server. */
class OrderRealtimeIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    AuthClient client;
    OrderTestKitchen k;
    WebSocketStompClient stomp;
    final List<Socket> sockets = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        k = new OrderTestKitchen(client);
        stomp = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        MappingJackson2MessageConverter jackson = new MappingJackson2MessageConverter();
        jackson.setObjectMapper(json);
        stomp.setMessageConverter(new CompositeMessageConverter(List.of(new StringMessageConverter(), jackson)));
    }

    @AfterEach
    void tearDown() {
        sockets.forEach(Socket::close);
        stomp.stop();
    }

    // ── CONNECT ──────────────────────────────────────────────────────────────

    @Test
    void connectRequiresAValidToken() {
        assertThatThrownBy(() -> connect(Map.of())).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect(Map.of("Authorization", "Bearer not-a-jwt"))).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect(Map.of("X-Guest-Order-Token", "nope"))).isInstanceOf(ExecutionException.class);
    }

    // ── restaurant topic ─────────────────────────────────────────────────────

    @Test
    void ownerSeesOrdersOnlyOncePaidAndThenEveryStatusChange() throws Exception {
        Socket owner = connectAs(k.owner).subscribe(restaurantTopic(k.restaurantId));

        JsonNode placed = k.placed(k.order("TAKEAWAY"));
        String id = placed.path("id").asText();
        owner.expectNothing(); // unpaid online order: invisible to the kitchen

        k.pay(id, "pay_ok").andExpect(status().isOk());
        JsonNode paid = owner.next();
        assertThat(paid.path("type").asText()).isEqualTo("ORDER_UPDATED");
        assertThat(paid.path("order").path("id").asText()).isEqualTo(id);
        assertThat(paid.path("order").path("paymentStatus").asText()).isEqualTo("PAID");

        k.ownerSets(id, "ACCEPTED").andExpect(status().isOk());
        assertThat(owner.next().path("order").path("status").asText()).isEqualTo("ACCEPTED");
        k.chefPrepares(id).andExpect(status().isOk());
        JsonNode preparing = owner.next().path("order");
        assertThat(preparing.path("status").asText()).isEqualTo("PREPARING");
        assertThat(preparing.path("preparation").path("chefId").asText()).isEqualTo(k.tandoor.chefId());
    }

    @Test
    void payLaterDineInReachesTheKitchenImmediately() throws Exception {
        Socket chef = connectAs(k.tandoor.chefToken()).subscribe(restaurantTopic(k.restaurantId));
        var body = k.order("DINE_IN");
        body.put("paymentMethod", "PAY_LATER");
        String id = k.placed(body).path("id").asText();

        JsonNode event = chef.next().path("order");
        assertThat(event.path("id").asText()).isEqualTo(id);
        assertThat(event.path("tableLabel").asText()).isEqualTo("T4");
    }

    @Test
    void restaurantTopicIsOnlyForThatRestaurantsOwnerAndChefs() throws Exception {
        OrderTestKitchen other = new OrderTestKitchen(client);
        connectAs(other.owner).subscribe(restaurantTopic(k.restaurantId)).expectDenied();
        connectAs(other.tandoor.chefToken()).subscribe(restaurantTopic(k.restaurantId)).expectDenied();
        connectAs(k.customer).subscribe(restaurantTopic(k.restaurantId)).expectDenied();
        String pendingOwner = client.registered("RESTAURANT_OWNER").path("accessToken").asText();
        connectAs(pendingOwner).subscribe(restaurantTopic(k.restaurantId)).expectDenied();
    }

    // ── order topic ──────────────────────────────────────────────────────────

    @Test
    void customerFollowsTheirOwnOrder() throws Exception {
        String id = k.placed(k.order("TAKEAWAY")).path("id").asText();
        Socket customer = connectAs(k.customer).subscribe("/topic/order/" + id);

        k.pay(id, "pay_ok").andExpect(status().isOk());
        assertThat(customer.next().path("order").path("paymentStatus").asText()).isEqualTo("PAID");
        k.ownerSets(id, "ACCEPTED").andExpect(status().isOk());
        assertThat(customer.next().path("order").path("status").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void orderTopicIsOnlyForThatOrdersCustomer() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        String otherCustomer = client.registered("CUSTOMER").path("accessToken").asText();

        connectAs(otherCustomer).subscribe("/topic/order/" + id).expectDenied();
        connectAs(k.owner).subscribe("/topic/order/" + id).expectDenied();
        connectAs(k.customer).subscribe("/topic/order/not-a-uuid").expectDenied();
        connectAs(k.customer).subscribe("/topic/anything-else").expectDenied();
    }

    @Test
    void guestTokenOpensOnlyItsOwnOrder() throws Exception {
        Map<String, Object> guest = new java.util.HashMap<>(Map.of("name", "Meera", "phone", AuthClient.randomPhone(),
                "tableSessionToken", k.tableSession, "paymentMethod", "PAY_LATER",
                "items", List.of(Map.of("menuItemId", k.dal, "quantity", 1))));
        JsonNode response = client.body(client.postJson("/api/guest/orders", guest).andExpect(status().isCreated()));
        String id = response.path("order").path("id").asText();
        String token = response.path("guestToken").asText();

        Socket socket = connect(Map.of("X-Guest-Order-Token", token)).subscribe("/topic/order/" + id);
        k.ownerSets(id, "ACCEPTED").andExpect(status().isOk());
        assertThat(socket.next().path("order").path("status").asText()).isEqualTo("ACCEPTED");

        String someoneElses = k.visibleOrder("TAKEAWAY");
        connect(Map.of("X-Guest-Order-Token", token)).subscribe("/topic/order/" + someoneElses).expectDenied();
        connect(Map.of("X-Guest-Order-Token", token)).subscribe(restaurantTopic(k.restaurantId)).expectDenied();
    }

    @Test
    void clientsCannotPublish() throws Exception {
        Socket owner = connectAs(k.owner);
        owner.session.send(restaurantTopic(k.restaurantId), Map.of("type", "ORDER_UPDATED", "order", Map.of("id", "fake")));
        owner.expectDenied();
    }

    @Test
    void restaurantRejectionIsPushedToTheCustomer() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        Socket customer = connectAs(k.customer).subscribe("/topic/order/" + id);
        client.send(POST, "/api/orders/" + id + "/cancel", k.customer, null).andExpect(status().isOk());

        JsonNode order = customer.next().path("order");
        assertThat(order.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(order.path("paymentStatus").asText()).isEqualTo("REFUND_PENDING");
        // ...and the refund completing is pushed too.
        assertThat(customer.next().path("order").path("paymentStatus").asText()).isEqualTo("REFUNDED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String restaurantTopic(String restaurantId) {
        return "/topic/restaurant/" + restaurantId + "/orders";
    }

    private Socket connectAs(String accessToken) throws Exception {
        return connect(Map.of("Authorization", "Bearer " + accessToken));
    }

    private Socket connect(Map<String, String> headers) throws Exception {
        Socket socket = new Socket();
        StompHeaders connectHeaders = new StompHeaders();
        headers.forEach(connectHeaders::add);
        socket.session = stomp.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders, socket.handler)
                .get(5, TimeUnit.SECONDS);
        sockets.add(socket);
        return socket;
    }

    final class Socket {
        StompSession session;
        final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

        final StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add(String.valueOf(headers.getFirst("message")));
            }

            @Override
            public void handleException(StompSession s, StompCommand command, StompHeaders headers, byte[] payload, Throwable e) {
                errors.add(String.valueOf(e.getMessage()));
            }

            @Override
            public void handleTransportError(StompSession s, Throwable e) {
                errors.add("transport: " + e.getMessage());
            }
        };

        Socket subscribe(String destination) throws InterruptedException {
            session.subscribe(destination, new StompSessionHandlerAdapter() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return JsonNode.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    messages.add((JsonNode) payload);
                }
            });
            Thread.sleep(300); // let the SUBSCRIBE reach the broker before events are triggered
            return this;
        }

        JsonNode next() throws InterruptedException {
            JsonNode message = messages.poll(5, TimeUnit.SECONDS);
            assertThat(message).as("expected a message; errors: %s", errors).isNotNull();
            return message;
        }

        void expectNothing() throws InterruptedException {
            assertThat(messages.poll(1, TimeUnit.SECONDS)).isNull();
        }

        void expectDenied() throws InterruptedException {
            String error = errors.poll(5, TimeUnit.SECONDS);
            assertThat(error).as("expected an ERROR frame").isNotNull().contains("Access denied");
            assertThat(messages).isEmpty();
        }

        void close() {
            try {
                if (session != null && session.isConnected()) session.disconnect();
            } catch (RuntimeException ignored) {
                // already closed by the server
            }
        }
    }
}
