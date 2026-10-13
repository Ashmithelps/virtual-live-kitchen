package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.clip.ClipService;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Station recording phones: pairing, device auth, footage windows on READY, uploads. */
class StationRecorderIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClipService clipService;

    AuthClient client;
    OrderTestKitchen k;
    WebSocketStompClient stomp;
    final List<StompSession> sessions = new ArrayList<>();
    final HttpClient http = HttpClient.newHttpClient();

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
        sessions.forEach(s -> {
            try {
                if (s.isConnected()) s.disconnect();
            } catch (RuntimeException ignored) {
                // closed by server
            }
        });
        stomp.stop();
    }

    // ── pairing & device auth ────────────────────────────────────────────────

    @Test
    void pairingCodeWorksOnceAndRePairingUnpairsTheOldPhone() throws Exception {
        JsonNode pairing = startPairing(k.tandoor.stationId());
        assertThat(pairing.path("pairingUrl").asText()).startsWith("http://localhost:3000/station#pair=");
        assertThat(pairing.path("qrSvgDataUri").asText()).startsWith("data:image/svg+xml;base64,");
        String code = pairing.path("pairingUrl").asText().substring(pairing.path("pairingUrl").asText().indexOf("#pair=") + 6);

        JsonNode paired = client.body(pair(code).andExpect(status().isOk()));
        String token = paired.path("deviceToken").asText();
        assertThat(paired.path("station").path("stationName").asText()).isEqualTo("Tandoor");
        pair(code).andExpect(status().isNotFound()); // one-time

        device(get("/api/station-device/me"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value(k.tandoor.stationId()))
                .andExpect(jsonPath("$.restaurantId").value(k.restaurantId));
        client.send(GET, "/api/owner/restaurants/" + k.restaurantId + "/stations", k.owner, null)
                .andExpect(jsonPath("$[0].deviceTokenIssued").value(true))
                .andExpect(jsonPath("$[0].devicePairedAt").isNotEmpty());
        // Any device call is a heartbeat the owner sees as "online".
        JsonNode station = client.body(client.send(GET, "/api/owner/restaurants/" + k.restaurantId + "/stations", k.owner, null)).get(0);
        assertThat(Instant.parse(station.path("deviceLastSeenAt").asText())).isAfter(Instant.now().minusSeconds(30));

        String newToken = pairDevice(k.tandoor.stationId());
        device(get("/api/station-device/me"), token).andExpect(status().isUnauthorized());
        device(get("/api/station-device/me"), newToken).andExpect(status().isOk());
    }

    @Test
    void expiredOrForeignPairingIsRefused() throws Exception {
        String url = startPairing(k.tandoor.stationId()).path("pairingUrl").asText();
        jdbc.update("update kitchen_stations set pairing_expires_at = now() - interval '1 minute' where id = ?::uuid", k.tandoor.stationId());
        pair(url.substring(url.indexOf("#pair=") + 6)).andExpect(status().isNotFound());

        OrderTestKitchen other = new OrderTestKitchen(client);
        client.send(POST, "/api/owner/restaurants/" + k.restaurantId + "/stations/" + k.tandoor.stationId() + "/pairing", other.owner, null)
                .andExpect(status().isNotFound());
        client.send(POST, "/api/owner/restaurants/" + k.restaurantId + "/stations/" + k.tandoor.stationId() + "/pairing", k.tandoor.chefToken(), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void deviceTokensOnlyWorkOnDeviceRoutes() throws Exception {
        String token = pairDevice(k.tandoor.stationId());
        device(get("/api/me"), token).andExpect(status().isUnauthorized());
        device(get("/api/chef/orders"), token).andExpect(status().isUnauthorized());
        client.send(GET, "/api/station-device/me", k.owner, null).andExpect(status().isForbidden());
        device(get("/api/station-device/me"), "not-a-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Device token is invalid (the station may have been paired with another phone)"));
    }

    @Test
    void serverTimeIsPublic() throws Exception {
        long before = System.currentTimeMillis();
        long server = client.body(mvc.perform(get("/api/time")).andExpect(status().isOk())).path("epochMillis").asLong();
        assertThat(server).isBetween(before - 1000, System.currentTimeMillis() + 1000);
    }

    // ── windows on READY ─────────────────────────────────────────────────────

    @Test
    void readyOrderPushesTheFootageWindowToTheStation() throws Exception {
        String token = pairDevice(k.tandoor.stationId());
        Socket station = connect(Map.of("X-Device-Token", token)).subscribe("/topic/station/" + k.tandoor.stationId());

        String id = k.visibleOrder("TAKEAWAY");
        k.advance(id, "ACCEPTED", "PREPARING");
        k.chefSets(id, Map.of("status", "READY")).andExpect(status().isOk()).andExpect(jsonPath("$.clipStatus").value("PENDING"));

        JsonNode message = station.next();
        assertThat(message.path("type").asText()).isEqualTo("CLIP_WINDOW");
        JsonNode window = message.path("window");
        assertThat(window.path("orderId").asText()).isEqualTo(id);
        JsonNode order = client.body(client.send(GET, "/api/orders/" + id, k.customer, null));
        Instant preparing = Instant.parse(order.path("timeline").path("preparingAt").asText());
        Instant ready = Instant.parse(order.path("timeline").path("readyAt").asText());
        assertThat(Instant.parse(window.path("windowStart").asText())).isEqualTo(preparing.minusSeconds(10));
        assertThat(Instant.parse(window.path("windowEnd").asText())).isEqualTo(ready.plusSeconds(5));

        // Missed pushes can be caught up on.
        device(get("/api/station-device/clip-requests"), token)
                .andExpect(jsonPath("$[0].orderId").value(id))
                .andExpect(jsonPath("$[0].displayNumber").value(order.path("displayNumber").asInt()));
    }

    @Test
    void noConsentMeansNoWindowAndNoClip() throws Exception {
        String token = pairDevice(k.tandoor.stationId());
        Socket station = connect(Map.of("X-Device-Token", token)).subscribe("/topic/station/" + k.tandoor.stationId());
        String id = k.visibleOrder("TAKEAWAY");
        k.advance(id, "ACCEPTED", "PREPARING");

        // The chef withdraws consent while cooking.
        client.send(POST, "/api/chef/consent/withdraw", k.tandoor.chefToken(), null).andExpect(status().isOk());
        k.chefSets(id, Map.of("status", "READY")).andExpect(status().isOk()).andExpect(jsonPath("$.clipStatus").value("NOT_AVAILABLE"));

        station.expectNothing();
        assertThat(clipReason(id)).isEqualTo("NO_CONSENT");
        device(get("/api/station-device/clip-requests"), token).andExpect(jsonPath("$.length()").value(0));
        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.clipStatus").value("NOT_AVAILABLE"));
    }

    @Test
    void stationWithoutAPhoneGetsNoClip() throws Exception {
        String id = readyOrder();
        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.clipStatus").value("NOT_AVAILABLE"));
        assertThat(clipReason(id)).isEqualTo("NO_DEVICE");
    }

    @Test
    void stationTopicsArePrivate() throws Exception {
        String token = pairDevice(k.tandoor.stationId());
        String other = k.fixtures.createStation(k.owner, k.restaurantId, "Grill").path("id").asText();
        connect(Map.of("X-Device-Token", token)).subscribe("/topic/station/" + other).expectDenied();
        connect(Map.of("Authorization", "Bearer " + k.owner)).subscribe("/topic/station/" + k.tandoor.stationId()).expectDenied();
        connect(Map.of("X-Device-Token", token)).subscribe("/topic/restaurant/" + k.restaurantId + "/orders").expectDenied();
    }

    // ── uploads ──────────────────────────────────────────────────────────────

    @Test
    void phoneUploadsOverlappingChunksThroughPresignedUrls() throws Exception {
        String token = pairDevice(k.tandoor.stationId());
        String id = readyOrder();
        JsonNode window = client.body(device(get("/api/station-device/clip-requests"), token)).get(0);
        Instant start = Instant.parse(window.path("windowStart").asText());
        byte[] video = "fake-webm-bytes-0123456789".getBytes();

        JsonNode url = client.body(uploadUrls(token, id, chunk("c1", start.minusSeconds(30), start.plusSeconds(30), "video/webm;codecs=vp8", video.length))
                .andExpect(status().isOk())).get(0);
        String key = url.path("objectKey").asText();
        assertThat(key).startsWith("raw/" + k.restaurantId + "/" + id + "/").endsWith("-c1.webm");

        // The URL is pinned to the declared size.
        assertThat(put(url, "too-long-for-the-signed-size-xxxxxxxx".getBytes()).statusCode()).isNotEqualTo(200);
        assertThat(put(url, video).statusCode()).isEqualTo(200);

        uploaded(token, id, List.of(key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.chunkCount").value(1));
        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.clipStatus").value("UPLOADED"));
        uploaded(token, id, List.of(key)).andExpect(status().isOk()).andExpect(jsonPath("$.chunkCount").value(1)); // idempotent
        device(get("/api/station-device/clip-requests"), token).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void uploadsAreConfinedToTheOrdersWindowPrefixAndRealObjects() throws Exception {
        String token = pairDevice(k.tandoor.stationId());
        String id = readyOrder();
        Instant start = Instant.parse(client.body(device(get("/api/station-device/clip-requests"), token)).get(0).path("windowStart").asText());

        uploadUrls(token, id, chunk("old", start.minusSeconds(600), start.minusSeconds(540), "video/webm", 10)).andExpect(status().isBadRequest());
        uploadUrls(token, id, chunk("img", start, start.plusSeconds(60), "image/png", 10)).andExpect(status().isBadRequest());
        uploadUrls(token, id, chunk("big", start, start.plusSeconds(60), "video/webm", 60L * 1024 * 1024)).andExpect(status().isBadRequest());

        uploaded(token, id, List.of("raw/" + k.restaurantId + "/" + UUID.randomUUID() + "/1-x.webm")).andExpect(status().isBadRequest());
        uploaded(token, id, List.of("media/whatever.webm")).andExpect(status().isBadRequest());
        uploaded(token, id, List.of("raw/" + k.restaurantId + "/" + id + "/123-never-uploaded.webm")).andExpect(status().isBadRequest());

        // Another station's phone can't touch this order.
        String grill = k.fixtures.createStation(k.owner, k.restaurantId, "Grill").path("id").asText();
        String otherToken = pairDevice(grill);
        uploadUrls(otherToken, id, chunk("c", start, start.plusSeconds(60), "video/webm", 10)).andExpect(status().isNotFound());
        uploaded(otherToken, id, List.of()).andExpect(status().isNotFound());
    }

    @Test
    void noFootageAndTimeoutsCloseTheRequest() throws Exception {
        String token = pairDevice(k.tandoor.stationId());
        String empty = readyOrder();
        uploaded(token, empty, List.of()).andExpect(jsonPath("$.status").value("NOT_AVAILABLE")).andExpect(jsonPath("$.reason").value("NO_FOOTAGE"));
        client.send(GET, "/api/orders/" + empty, k.customer, null).andExpect(jsonPath("$.clipStatus").value("NOT_AVAILABLE"));

        String stale = readyOrder();
        jdbc.update("update clip_requests set requested_at = now() - interval '3 hours' where order_id = ?::uuid", stale);
        clipService.expireStaleRequests();
        assertThat(clipReason(stale)).isEqualTo("UPLOAD_TIMEOUT");
        client.send(GET, "/api/orders/" + stale, k.customer, null).andExpect(jsonPath("$.clipStatus").value("NOT_AVAILABLE"));
        uploaded(token, stale, List.of()).andExpect(status().isConflict());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String readyOrder() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        k.advance(id, "ACCEPTED", "PREPARING");
        k.chefSets(id, Map.of("status", "READY")).andExpect(status().isOk());
        return id;
    }

    private JsonNode startPairing(String stationId) throws Exception {
        return client.body(client.send(POST, "/api/owner/restaurants/" + k.restaurantId + "/stations/" + stationId + "/pairing",
                k.owner, null).andExpect(status().isOk()));
    }

    private ResultActions pair(String code) throws Exception {
        return client.postJson("/api/station-device/pair", Map.of("pairingCode", code));
    }

    private String pairDevice(String stationId) throws Exception {
        String url = startPairing(stationId).path("pairingUrl").asText();
        return client.body(pair(url.substring(url.indexOf("#pair=") + 6)).andExpect(status().isOk())).path("deviceToken").asText();
    }

    private ResultActions device(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("X-Device-Token", token));
    }

    private static Map<String, Object> chunk(String id, Instant start, Instant end, String type, long size) {
        return Map.of("chunks", List.of(Map.of("chunkId", id, "startedAt", start.toString(), "endedAt", end.toString(),
                "contentType", type, "sizeBytes", size)));
    }

    private ResultActions uploadUrls(String token, String orderId, Map<String, Object> body) throws Exception {
        return device(post("/api/clips/" + orderId + "/upload-urls").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), token);
    }

    private ResultActions uploaded(String token, String orderId, List<String> keys) throws Exception {
        return device(post("/api/clips/" + orderId + "/chunks-uploaded").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("objectKeys", keys))), token);
    }

    private HttpResponse<String> put(JsonNode url, byte[] body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url.path("uploadUrl").asText()))
                .header("Content-Type", url.path("headers").path("Content-Type").asText())
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String clipReason(String orderId) {
        return jdbc.queryForObject("select reason from clip_requests where order_id = ?::uuid", String.class, orderId);
    }

    private Socket connect(Map<String, String> headers) throws Exception {
        Socket socket = new Socket();
        StompHeaders connectHeaders = new StompHeaders();
        headers.forEach(connectHeaders::add);
        socket.session = stomp.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders, socket.handler)
                .get(5, TimeUnit.SECONDS);
        sessions.add(socket.session);
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
            public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable e) {
                errors.add(String.valueOf(e.getMessage()));
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
            Thread.sleep(300);
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
        }
    }
}
