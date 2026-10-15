package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Watching, downloading and sharing READY clips: who gets 10-minute links, the public share page data, view counting
 * and revoking. The READY clip is set up directly (rendering is covered by ClipPipelineIT).
 */
class ClipShareIT extends IntegrationTest {

    private static final byte[] VIDEO = "....ftypisom fake mp4 bytes for the test".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] THUMB = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectStorage storage;
    @TempDir Path temp;

    AuthClient client;
    OrderTestKitchen k;
    final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        k = new OrderTestKitchen(client);
    }

    @Test
    void onlyTheCustomerOwnerAndAdminGetShortLivedWatchAndDownloadLinks() throws Exception {
        String id = readyClip(readyOrder());
        String path = "/api/orders/" + id + "/clip";

        JsonNode playback = client.body(client.send(GET, path, k.customer, null).andExpect(status().isOk()));
        Instant expires = Instant.parse(playback.path("linksExpireAt").asText());
        assertThat(expires).isBetween(Instant.now().plus(Duration.ofMinutes(9)), Instant.now().plus(Duration.ofMinutes(11)));
        assertThat(playback.path("videoUrl").asText()).contains("X-Amz-Expires=600");
        assertThat(download(playback.path("videoUrl").asText()).body()).isEqualTo(VIDEO);
        assertThat(download(playback.path("thumbnailUrl").asText()).body()).isEqualTo(THUMB);
        assertThat(playback.path("share").isNull()).isTrue();
        assertThat(playback.path("chefName").asText()).isNotBlank();

        String filename = playback.path("downloadFilename").asText();
        assertThat(filename).matches("[a-z0-9-]+-order-" + playback.path("displayNumber").asInt() + "\\.mp4");
        HttpResponse<byte[]> download = download(playback.path("downloadUrl").asText());
        assertThat(download.headers().firstValue("Content-Disposition")).hasValueSatisfying(v ->
                assertThat(v).contains("attachment").contains(filename));
        assertThat(download.body()).isEqualTo(VIDEO);

        // Order responses (which chefs also see) carry only the thumbnail.
        client.send(GET, "/api/orders/" + id, k.customer, null)
                .andExpect(jsonPath("$.clip.thumbnailUrl").isNotEmpty())
                .andExpect(jsonPath("$.clip.videoUrl").doesNotExist());

        // Nobody else gets watch links.
        String stranger = client.registered("CUSTOMER").path("accessToken").asText();
        client.send(GET, path, stranger, null).andExpect(status().isNotFound());
        client.send(GET, path, k.tandoor.chefToken(), null).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());

        String owner = "/api/owner/restaurants/" + k.restaurantId + "/clips/" + id + "/playback";
        client.send(GET, owner, k.owner, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.videoUrl").isNotEmpty())
                .andExpect(jsonPath("$.share").doesNotExist());
        OrderTestKitchen other = new OrderTestKitchen(client);
        client.send(GET, owner, other.owner, null).andExpect(status().isNotFound());
        client.send(GET, "/api/owner/restaurants/" + other.restaurantId + "/clips/" + id + "/playback", other.owner, null)
                .andExpect(status().isNotFound());
        client.send(GET, owner, k.tandoor.chefToken(), null).andExpect(status().isForbidden());

        client.send(GET, "/api/admin/clips/" + id + "/playback", client.adminToken(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty());
        client.send(GET, "/api/admin/clips/" + id + "/playback", k.owner, null).andExpect(status().isForbidden());

        // Not ready / expired.
        String notReady = readyOrder();
        client.send(GET, "/api/orders/" + notReady + "/clip", k.customer, null).andExpect(status().isConflict());
        jdbc.update("update clips set status = 'EXPIRED' where order_id = ?::uuid", id);
        client.send(GET, path, k.customer, null).andExpect(status().isGone());
    }

    @Test
    void customerSharesAPublicLinkThatCountsViewsAndCanBeRevoked() throws Exception {
        String id = readyClip(readyOrder());
        String share = "/api/orders/" + id + "/clip/share";

        JsonNode link = client.body(client.send(POST, share, k.customer, null).andExpect(status().isOk()));
        String token = link.path("token").asText();
        assertThat(token).hasSizeGreaterThanOrEqualTo(20);
        assertThat(link.path("url").asText()).isEqualTo("http://localhost:3000/c/" + token);
        assertThat(client.body(client.send(POST, share, k.customer, null)).path("token").asText()).isEqualTo(token); // idempotent
        client.send(GET, "/api/orders/" + id + "/clip", k.customer, null).andExpect(jsonPath("$.share.token").value(token));

        // Anyone with the link: clip, restaurant, chef; nothing about the customer.
        JsonNode pub = client.body(mvc.perform(get("/api/public/clips/" + token)).andExpect(status().isOk()));
        assertThat(pub.path("title").asText()).startsWith("Watch my order being made at ");
        assertThat(pub.path("restaurantName").asText()).isNotBlank();
        assertThat(pub.path("restaurantOrderable").asBoolean()).isTrue();
        assertThat(pub.path("chefName").asText()).isNotBlank();
        assertThat(download(pub.path("videoUrl").asText()).body()).isEqualTo(VIDEO);
        String customerPhone = client.body(client.send(GET, "/api/me", k.customer, null)).path("phone").asText();
        assertThat(pub.toString()).doesNotContain(customerPhone);
        assertThat(pub.has("orderId") || pub.has("customerName") || pub.has("displayNumber")).isFalse();

        // The chef link works for anyone too.
        mvc.perform(get("/api/public/chefs/" + pub.path("chefId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(pub.path("chefName").asText()))
                .andExpect(jsonPath("$.restaurantSlug").value(pub.path("restaurantSlug").asText()));

        // Views: once per visitor per day.
        view(token, "visitor-aaaa").andExpect(status().isNoContent());
        view(token, "visitor-aaaa").andExpect(status().isNoContent());
        view(token, "visitor-bbbb").andExpect(status().isNoContent());
        assertThat(views(id)).isEqualTo(2);
        jdbc.update("update clip_views set view_date = view_date - 1 where clip_id = (select id from clips where order_id = ?::uuid)", id);
        view(token, "visitor-aaaa").andExpect(status().isNoContent());
        assertThat(views(id)).isEqualTo(3);
        view(token, "x").andExpect(status().isBadRequest());
        client.send(GET, "/api/orders/" + id + "/clip", k.customer, null).andExpect(jsonPath("$.viewCount").value(3));

        JsonNode row = ownerRow(id);
        assertThat(row.path("viewCount").asLong()).isEqualTo(3);
        assertThat(row.path("shareCount").asLong()).isEqualTo(1);
        assertThat(row.path("shared").asBoolean()).isTrue();

        // Only the order's customer controls the link.
        String stranger = client.registered("CUSTOMER").path("accessToken").asText();
        client.send(POST, share, stranger, null).andExpect(status().isNotFound());
        client.send(DELETE, share, stranger, null).andExpect(status().isNotFound());

        // Revoke: the link stops working; sharing again makes a new one.
        client.send(DELETE, share, k.customer, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/clips/" + token)).andExpect(status().isNotFound());
        view(token, "visitor-cccc").andExpect(status().isNotFound());
        String second = client.body(client.send(POST, share, k.customer, null)).path("token").asText();
        assertThat(second).isNotEqualTo(token);
        mvc.perform(get("/api/public/clips/" + token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/clips/" + second)).andExpect(status().isOk());
        assertThat(ownerRow(id).path("shareCount").asLong()).isEqualTo(2);

        // Expired clips: the page says so.
        jdbc.update("update clips set status = 'EXPIRED' where order_id = ?::uuid", id);
        mvc.perform(get("/api/public/clips/" + second)).andExpect(status().isGone());
        mvc.perform(get("/api/public/clips/no-such-token")).andExpect(status().isNotFound());
    }

    @Test
    void guestsWatchAndShareWithTheirOrderToken() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("name", "Guest", "phone", AuthClient.randomPhone(),
                "tableSessionToken", k.tableSession, "paymentMethod", "PAY_LATER",
                "items", List.of(Map.of("menuItemId", k.dal, "quantity", 1))));
        JsonNode placed = client.body(client.postJson("/api/guest/orders", body).andExpect(status().isCreated()));
        String id = placed.path("order").path("id").asText();
        String guestToken = placed.path("guestToken").asText();
        k.advance(id, "ACCEPTED", "PREPARING");
        k.chefSets(id, Map.of("status", "READY")).andExpect(status().isOk());
        readyClip(id);

        String base = "/api/guest/orders/" + id + "/clip";
        mvc.perform(get(base).header("X-Guest-Order-Token", guestToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.videoUrl").isNotEmpty());
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        String token = client.body(mvc.perform(post(base + "/share").header("X-Guest-Order-Token", guestToken)).andExpect(status().isOk()))
                .path("token").asText();
        mvc.perform(get("/api/public/clips/" + token)).andExpect(status().isOk());
        mvc.perform(delete(base + "/share").header("X-Guest-Order-Token", guestToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/clips/" + token)).andExpect(status().isNotFound());
    }

    @Test
    void chefProfilesArePublicOnlyForActiveChefsOfLiveRestaurants() throws Exception {
        mvc.perform(get("/api/public/chefs/" + k.tandoor.chefId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").isNotEmpty());
        jdbc.update("update chefs set removed_at = now() where id = ?::uuid", k.tandoor.chefId());
        mvc.perform(get("/api/public/chefs/" + k.tandoor.chefId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/chefs/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String readyOrder() throws Exception {
        String id = k.visibleOrder("TAKEAWAY");
        k.advance(id, "ACCEPTED", "PREPARING");
        k.chefSets(id, Map.of("status", "READY")).andExpect(status().isOk());
        return id;
    }

    /** Turns the order's clip row into a READY clip with tiny stand-in files in the clips bucket. */
    private String readyClip(String orderId) throws Exception {
        String video = "clips/" + k.restaurantId + "/" + orderId + ".mp4";
        String thumb = "clips/" + k.restaurantId + "/" + orderId + ".jpg";
        Path v = Files.write(temp.resolve(orderId + ".mp4"), VIDEO);
        Path t = Files.write(temp.resolve(orderId + ".jpg"), THUMB);
        storage.uploadClip(video, v, "video/mp4");
        storage.uploadClip(thumb, t, "image/jpeg");
        assertThat(jdbc.update("""
                update clips set status = 'READY', video_key = ?, thumb_key = ?, duration_s = 42.5, failure_reason = null,
                       ready_at = now(), expires_at = now() + interval '30 days' where order_id = ?::uuid
                """, video, thumb, orderId)).isEqualTo(1);
        jdbc.update("update orders set clip_status = 'READY' where id = ?::uuid", orderId);
        return orderId;
    }

    private org.springframework.test.web.servlet.ResultActions view(String token, String visitor) throws Exception {
        return client.postJson("/api/public/clips/" + token + "/views", Map.of("visitorId", visitor));
    }

    private long views(String orderId) {
        Long n = jdbc.queryForObject("select count(*) from clip_views v join clips c on c.id = v.clip_id where c.order_id = ?::uuid",
                Long.class, orderId);
        return n == null ? 0 : n;
    }

    private JsonNode ownerRow(String orderId) throws Exception {
        JsonNode page = client.body(client.send(GET, "/api/owner/restaurants/" + k.restaurantId + "/clips?size=100", k.owner, null));
        for (JsonNode row : page.path("items")) {
            if (row.path("orderId").asText().equals(orderId)) return row;
        }
        throw new AssertionError("no clip row for " + orderId);
    }

    private HttpResponse<byte[]> download(String url) throws Exception {
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).as(url).isEqualTo(200);
        return response;
    }
}
