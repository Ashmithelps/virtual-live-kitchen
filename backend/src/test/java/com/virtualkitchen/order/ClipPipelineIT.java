package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.common.jobs.JobWorker;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.clip.ClipRetentionJob;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole clip pipeline against real Postgres + S3 + FFmpeg: the phone uploads the two sample webm chunks, the job
 * worker (driven by hand here; threads are off in tests) renders and publishes the clip, the customer is pushed the
 * links, raw footage is removed, and retention later expires the clip.
 */
class ClipPipelineIT extends IntegrationTest {

    private static final String WORKER = "it-worker";

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobWorker worker;
    @Autowired ClipRetentionJob retention;
    @Autowired ObjectStorage storage;

    AuthClient client;
    OrderTestKitchen k;
    WebSocketStompClient stomp;
    final List<StompSession> sessions = new ArrayList<>();
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void requireFfmpeg() {
        assumeTrue(onPath("ffmpeg") && onPath("ffprobe"), "ffmpeg/ffprobe not installed");
    }

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        k = new OrderTestKitchen(client);
        // Other test classes leave clip jobs for fake footage behind; only this test's jobs should run.
        jdbc.update("update jobs set status = 'DEAD', last_error = 'cleared by ClipPipelineIT' where status in ('QUEUED', 'RUNNING')");
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

    @Test
    void uploadedChunksBecomeAStampedClipThatIsPushedToTheCustomer() throws Exception {
        String token = pairDevice();
        String id = readyOrder();
        Socket customer = connect(Map.of("Authorization", "Bearer " + k.customer)).subscribe("/topic/order/" + id);

        JsonNode window = client.body(device(get("/api/station-device/clip-requests"), token)).get(0);
        Instant start = Instant.parse(window.path("windowStart").asText());
        Instant end = Instant.parse(window.path("windowEnd").asText());
        // Two back-to-back 30 s recordings straddling the window, like the phone's rolling chunks.
        List<String> keys = upload(token, id, List.of(
                new Sample("c1", "chunk-1.webm", start.minusSeconds(20)),
                new Sample("c2", "chunk-2.webm", start.plusSeconds(10))));
        uploaded(token, id, keys).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UPLOADED"));
        assertThat(jobStatus(id)).isEqualTo("QUEUED");

        assertThat(worker.runOnce(WORKER)).isTrue();
        assertThat(jobStatus(id)).isEqualTo("SUCCEEDED");

        // Pushed: PROCESSING first, then READY with the links.
        JsonNode pushed = customer.until(m -> "READY".equals(m.path("order").path("clipStatus").asText())).path("order");
        assertThat(pushed.path("clip").path("videoUrl").asText()).isNotBlank();

        JsonNode order = client.body(client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(status().isOk()));
        assertThat(order.path("clipStatus").asText()).isEqualTo("READY");
        JsonNode clip = order.path("clip");
        double windowS = Duration.between(start, end).toMillis() / 1000.0;
        assertThat(clip.path("durationSeconds").asDouble()).isBetween(windowS - 1, windowS + 1);
        assertThat(Instant.parse(clip.path("expiresAt").asText())).isAfter(Instant.now().plus(Duration.ofDays(29)));

        byte[] video = fetch(clip.path("videoUrl").asText());
        assertThat(new String(video, 4, 4, StandardCharsets.US_ASCII)).isEqualTo("ftyp"); // MP4
        byte[] thumb = fetch(clip.path("thumbnailUrl").asText());
        assertThat(thumb[0] & 0xff).isEqualTo(0xFF); // JPEG SOI
        assertThat(thumb[1] & 0xff).isEqualTo(0xD8);

        String videoKey = "clips/" + k.restaurantId + "/" + id + ".mp4";
        assertThat(jdbc.queryForObject("select video_key from clips where order_id = ?::uuid", String.class, id)).isEqualTo(videoKey);
        assertThat(jdbc.queryForObject("select thumb_key from clips where order_id = ?::uuid", String.class, id))
                .isEqualTo("clips/" + k.restaurantId + "/" + id + ".jpg");
        assertThat(jdbc.queryForObject("select chef_id is not null and station_id is not null from clips where order_id = ?::uuid",
                Boolean.class, id)).isTrue();

        // The owner's clip list has it with links and who made it.
        JsonNode owned = client.body(client.send(GET, ownerClips() + "?status=READY", k.owner, null).andExpect(status().isOk()));
        JsonNode row = find(owned.path("items"), id);
        assertThat(row.path("displayNumber").asInt()).isEqualTo(order.path("displayNumber").asInt());
        assertThat(row.path("thumbnailUrl").asText()).isNotBlank();
        assertThat(row.path("videoUrl").asText()).isNotBlank();
        assertThat(row.path("stationName").asText()).isEqualTo("Tandoor");
        assertThat(row.path("chefName").asText()).isNotBlank();
        assertThat(row.path("retryable").asBoolean()).isFalse();
        client.send(POST, ownerClips() + "/" + id + "/retry", k.owner, null).andExpect(status().isConflict());
        OrderTestKitchen other = new OrderTestKitchen(client);
        client.send(GET, "/api/owner/restaurants/" + k.restaurantId + "/clips", other.owner, null).andExpect(status().isNotFound());
        client.send(GET, ownerClips(), k.tandoor.chefToken(), null).andExpect(status().isForbidden());

        // Raw chunks are gone once the clip is published.
        keys.forEach(key -> assertThat(storage.clipExists(key)).as(key).isFalse());
        assertThat(jdbc.queryForObject("select raw_deleted_at is not null from clip_requests where order_id = ?::uuid", Boolean.class, id))
                .isTrue();

        // Retention: once past expires_at the clip and its files are deleted.
        jdbc.update("update clips set expires_at = now() - interval '1 minute' where order_id = ?::uuid", id);
        assertThat(retention.expireClips()).isGreaterThanOrEqualTo(1);
        assertThat(storage.clipExists(videoKey)).isFalse();
        client.send(GET, "/api/orders/" + id, k.customer, null)
                .andExpect(jsonPath("$.clipStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.clip").doesNotExist());
    }

    @Test
    void unreadableFootageEndsAsFailedAfterTheLastAttempt() throws Exception {
        String token = pairDevice();
        String id = readyOrder();
        Instant start = Instant.parse(client.body(device(get("/api/station-device/clip-requests"), token)).get(0).path("windowStart").asText());
        List<String> keys = upload(token, id, List.of(new Sample("bad", null, start)));
        uploaded(token, id, keys).andExpect(status().isOk());

        jdbc.update("update jobs set max_attempts = 2 where dedupe_key = ?", "clip:" + id);
        assertThat(worker.runOnce(WORKER)).isTrue();
        assertThat(jobStatus(id)).isEqualTo("QUEUED"); // retried later
        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.clipStatus").value("PROCESSING"));

        jdbc.update("update jobs set run_after = now() where dedupe_key = ?", "clip:" + id);
        assertThat(worker.runOnce(WORKER)).isTrue();
        assertThat(jobStatus(id)).isEqualTo("DEAD");
        client.send(GET, "/api/orders/" + id, k.customer, null)
                .andExpect(jsonPath("$.clipStatus").value("FAILED"))
                .andExpect(jsonPath("$.clip").doesNotExist());

        // The owner sees why and can put it back in the queue while the raw footage exists.
        JsonNode failed = find(client.body(client.send(GET, ownerClips() + "?status=FAILED", k.owner, null)).path("items"), id);
        assertThat(failed.path("failureReason").asText()).isNotBlank();
        assertThat(failed.path("retryable").asBoolean()).isTrue();
        client.send(POST, ownerClips() + "/" + id + "/retry", k.owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.failureReason").doesNotExist());
        assertThat(jobStatus(id)).isEqualTo("QUEUED");
        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.clipStatus").value("UPLOADED"));
        client.send(POST, ownerClips() + "/" + id + "/retry", k.owner, null).andExpect(status().isConflict());
        jdbc.update("update jobs set max_attempts = 1 where dedupe_key = ? and status = 'QUEUED'", "clip:" + id);
        assertThat(worker.runOnce(WORKER)).isTrue();
        client.send(GET, "/api/orders/" + id, k.customer, null).andExpect(jsonPath("$.clipStatus").value("FAILED"));

        // The garbage stays until the 24 h raw retention runs.
        assertThat(storage.clipExists(keys.get(0))).isTrue();
        jdbc.update("update clip_requests set requested_at = now() - interval '25 hours' where order_id = ?::uuid", id);
        assertThat(retention.purgeOldRawFootage()).isGreaterThanOrEqualTo(1);
        assertThat(storage.clipExists(keys.get(0))).isFalse();
        assertThat(jdbc.queryForObject("select raw_deleted_at is not null from clip_requests where order_id = ?::uuid", Boolean.class, id))
                .isTrue();
    }

    @Test
    void ordersSharingAWindowEachGetTheirOwnClip() throws Exception {
        String token = pairDevice();
        String first = k.visibleOrder("TAKEAWAY");
        String second = k.visibleOrder("TAKEAWAY");
        k.advance(first, "ACCEPTED", "PREPARING");
        k.advance(second, "ACCEPTED", "PREPARING");
        k.chefSets(first, Map.of("status", "READY")).andExpect(status().isOk());
        k.chefSets(second, Map.of("status", "READY")).andExpect(status().isOk());

        JsonNode windows = client.body(device(get("/api/station-device/clip-requests"), token));
        assertThat(windows).hasSize(2);
        for (String id : List.of(first, second)) {
            JsonNode window = null;
            for (JsonNode w : windows) {
                if (w.path("orderId").asText().equals(id)) window = w;
            }
            assertThat(window).as("window for " + id).isNotNull();
            Instant start = Instant.parse(window.path("windowStart").asText());
            uploaded(token, id, upload(token, id, List.of(new Sample("c1", "chunk-1.webm", start.minusSeconds(5)))))
                    .andExpect(status().isOk());
        }
        assertThat(worker.runOnce(WORKER)).isTrue();
        assertThat(worker.runOnce(WORKER)).isTrue();

        List<String> videoKeys = jdbc.queryForList("select video_key from clips where order_id in (?::uuid, ?::uuid) and status = 'READY'",
                String.class, first, second);
        assertThat(videoKeys).containsExactlyInAnyOrder(
                "clips/" + k.restaurantId + "/" + first + ".mp4", "clips/" + k.restaurantId + "/" + second + ".mp4");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String ownerClips() {
        return "/api/owner/restaurants/" + k.restaurantId + "/clips";
    }

    private static JsonNode find(JsonNode items, String orderId) {
        for (JsonNode item : items) {
            if (item.path("orderId").asText().equals(orderId)) return item;
        }
        throw new AssertionError("no clip for " + orderId + " in " + items);
    }

    /** {@code resource} null uploads bytes that are not a video. */
    record Sample(String chunkId, String resource, Instant startedAt) {
    }

    private List<String> upload(String token, String orderId, List<Sample> samples) throws Exception {
        List<String> keys = new ArrayList<>();
        for (Sample sample : samples) {
            byte[] bytes = sample.resource() == null
                    ? "definitely not a webm file".getBytes(StandardCharsets.UTF_8)
                    : Files.readAllBytes(Path.of(getClass().getResource("/clips/" + sample.resource()).toURI()));
            Map<String, Object> body = Map.of("chunks", List.of(Map.of("chunkId", sample.chunkId(),
                    "startedAt", sample.startedAt().toString(), "endedAt", sample.startedAt().plusSeconds(30).toString(),
                    "contentType", "video/webm", "sizeBytes", bytes.length)));
            JsonNode url = client.body(device(post("/api/clips/" + orderId + "/upload-urls").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)), token).andExpect(status().isOk())).get(0);
            HttpResponse<String> put = http.send(HttpRequest.newBuilder(URI.create(url.path("uploadUrl").asText()))
                    .header("Content-Type", url.path("headers").path("Content-Type").asText())
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(put.statusCode()).as(put.body()).isEqualTo(200);
            keys.add(url.path("objectKey").asText());
        }
        return keys;
    }

    private ResultActions uploaded(String token, String orderId, List<String> keys) throws Exception {
        return device(post("/api/clips/" + orderId + "/chunks-uploaded").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("objectKeys", keys))), token);
    }

    private byte[] fetch(String url) throws Exception {
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().length).isGreaterThan(100);
        return response.body();
    }

    private String readyOrder() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        k.advance(id, "ACCEPTED", "PREPARING");
        k.chefSets(id, Map.of("status", "READY")).andExpect(status().isOk()).andExpect(jsonPath("$.clipStatus").value("PENDING"));
        return id;
    }

    private String pairDevice() throws Exception {
        String url = client.body(client.send(POST, "/api/owner/restaurants/" + k.restaurantId + "/stations/" + k.tandoor.stationId()
                + "/pairing", k.owner, null).andExpect(status().isOk())).path("pairingUrl").asText();
        return client.body(client.postJson("/api/station-device/pair", Map.of("pairingCode", url.substring(url.indexOf("#pair=") + 6)))
                .andExpect(status().isOk())).path("deviceToken").asText();
    }

    private ResultActions device(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token)
            throws Exception {
        return mvc.perform(request.header("X-Device-Token", token));
    }

    private String jobStatus(String orderId) {
        return jdbc.queryForObject("select status from jobs where dedupe_key = ? order by created_at desc limit 1", String.class,
                "clip:" + orderId);
    }

    private static boolean onPath(String binary) {
        try {
            return new ProcessBuilder(binary, "-version").redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    private Socket connect(Map<String, String> headers) throws Exception {
        Socket socket = new Socket();
        StompHeaders connectHeaders = new StompHeaders();
        headers.forEach(connectHeaders::add);
        StompSession session = stomp.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        socket.session = session;
        return socket;
    }

    final class Socket {
        StompSession session;
        final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();

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

        JsonNode until(java.util.function.Predicate<JsonNode> match) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            List<JsonNode> seen = new ArrayList<>();
            while (System.nanoTime() < deadline) {
                JsonNode message = messages.poll(500, TimeUnit.MILLISECONDS);
                if (message != null && match.test(message)) {
                    return message;
                }
                if (message != null) seen.add(message);
            }
            throw new AssertionError("no matching message; saw " + seen);
        }
    }
}
