package com.virtualkitchen.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.clip.ClipReadyEvent;
import com.virtualkitchen.common.jobs.JobWorker;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.hygiene.HygieneJob;
import com.virtualkitchen.support.AuthClient;
import com.virtualkitchen.support.FakeAiService;
import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hygiene scoring end to end against a fake AI service: queueing, storing, rolling scores, public gating, disputes. */
@TestPropertySource(properties = {
        "app.hygiene.enabled=true",
        "app.hygiene.api-key=test-ai-key",
        "app.hygiene.min-public-clips=3",
})
class HygieneIT extends IntegrationTest {

    static final FakeAiService AI = new FakeAiService();
    static final String WORKER = "hygiene-it";

    @DynamicPropertySource
    static void aiUrl(DynamicPropertyRegistry registry) {
        registry.add("app.hygiene.service-url", AI::baseUrl);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobWorker worker;
    @Autowired ObjectStorage storage;
    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate tx;
    @TempDir Path temp;

    AuthClient client;
    OrderTestKitchen k;

    @BeforeEach
    void setUp() throws Exception {
        client = new AuthClient(mvc, json);
        k = new OrderTestKitchen(client);
        jdbc.update("update jobs set status = 'DEAD', last_error = 'cleared by HygieneIT' where status in ('QUEUED', 'RUNNING')");
        jdbc.update("delete from platform_settings");
        AI.status = 200;
        AI.body = FakeAiService.response(80.0, 100.0, null, List.of(3.0, 9.0, 15.0));
    }

    @Test
    void readyClipsAreAnalysedInTheirOwnLaneAndStored() throws Exception {
        UUID clipId = readyClip();
        ready(clipId);
        assertThat(jobStatus(clipId)).isEqualTo("QUEUED");

        // The clip lane never picks up hygiene work.
        assertThat(worker.runOnce(WORKER, List.of("CLIP_PROCESS"))).isFalse();
        assertThat(worker.runOnce(WORKER, List.of(HygieneJob.TYPE))).isTrue();
        assertThat(jobStatus(clipId)).isEqualTo("SUCCEEDED");

        FakeAiService.Call call = AI.calls.getLast();
        assertThat(call.apiKey()).isEqualTo("test-ai-key");
        assertThat(call.videoStatus()).as("the presigned video link works").isEqualTo(200);
        Map<String, Object> row = jdbc.queryForMap("select * from hygiene_results where clip_id = ?", clipId);
        assertThat(row.get("model_version")).isEqualTo("fake-ppe.pt@abc123");
        assertThat(((Number) row.get("gloves_pct")).doubleValue()).isEqualTo(80.0);
        assertThat(row.get("mask_pct")).isNull();
        assertThat(((Number) row.get("score")).doubleValue()).isEqualTo(90.0); // mean of 80 and 100; mask not judged
        assertThat(row.get("violations").toString()).contains("\"gloves\": [3.0, 9.0, 15.0]");

        // Re-running (e.g. after a crash) doesn't duplicate it.
        ready(clipId);
        assertThat(worker.runOnce(WORKER, List.of(HygieneJob.TYPE))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from hygiene_results where clip_id = ?", Long.class, clipId)).isEqualTo(1);
    }

    @Test
    void failuresLeaveTheClipUnscoredAndNeverTouchTheOrder() throws Exception {
        UUID unreadable = readyClip();
        ready(unreadable);
        AI.status = 422;
        AI.body = "{\"status\":422,\"message\":\"The file isn't a readable video\"}";
        assertThat(worker.runOnce(WORKER, List.of(HygieneJob.TYPE))).isTrue();
        assertThat(jobStatus(unreadable)).isEqualTo("DEAD"); // permanent: no point retrying

        UUID down = readyClip();
        ready(down);
        AI.status = 503;
        AI.body = "{\"detail\":\"No detection model is installed\"}";
        assertThat(worker.runOnce(WORKER, List.of(HygieneJob.TYPE))).isTrue();
        assertThat(jobStatus(down)).isEqualTo("QUEUED"); // retried later with backoff

        assertThat(jdbc.queryForObject("select count(*) from hygiene_results where clip_id in (?, ?)", Long.class, unreadable, down)).isZero();
        String orderId = jdbc.queryForObject("select order_id::text from clips where id = ?", String.class, unreadable);
        client.send(GET, "/api/orders/" + orderId, k.customer, null).andExpect(jsonPath("$.clipStatus").value("READY"));
    }

    @Test
    void ownerSeesRollingScoresPublicOnlyAfterAdminEnablesAndEnoughClips() throws Exception {
        String base = "/api/owner/restaurants/" + k.restaurantId + "/hygiene";
        scored(90.0, 100.0, 100.0);   // 96.7
        scored(50.0, 100.0, null);    // 75.0
        scored(null, null, null);     // nothing judged: not counted

        JsonNode overview = client.body(client.send(GET, base, k.owner, null).andExpect(status().isOk()));
        assertThat(overview.path("clipCount").asLong()).isEqualTo(2);
        assertThat(overview.path("score").asDouble()).isEqualTo(85.9); // (96.7 + 75.0) / 2 = 85.85
        assertThat(overview.path("publicScoresEnabled").asBoolean()).isFalse();
        assertThat(overview.path("worst").get(0).path("effectiveScore").asDouble()).isEqualTo(75.0);
        assertThat(overview.path("worst").get(0).path("violations").path("gloves").toString()).isEqualTo("[3.0,9.0,15.0]");
        assertThat(overview.path("chefs").get(0).path("clipCount").asLong()).isEqualTo(2);
        assertThat(overview.path("trend").size()).isGreaterThanOrEqualTo(1);

        String slug = client.body(client.send(GET, "/api/owner/restaurants/" + k.restaurantId, k.owner, null)).path("slug").asText();
        mvc.perform(get("/api/restaurants/" + slug)).andExpect(jsonPath("$.hygiene").doesNotExist());

        // Admin turns public scores on; still hidden below the minimum number of clips (3 in this test).
        String admin = client.adminToken();
        client.send(PUT, "/api/admin/hygiene/settings", admin, Map.of("publicScoresEnabled", true)).andExpect(jsonPath("$.publicScoresEnabled").value(true));
        client.send(PUT, "/api/admin/hygiene/settings", k.owner, Map.of("publicScoresEnabled", false)).andExpect(status().isForbidden());
        mvc.perform(get("/api/restaurants/" + slug)).andExpect(jsonPath("$.hygiene").doesNotExist());

        scored(100.0, 100.0, 100.0);
        mvc.perform(get("/api/restaurants/" + slug)).andExpect(jsonPath("$.hygiene.clipCount").value(3))
                .andExpect(jsonPath("$.hygiene.score").value(90.6)); // (96.7 + 75 + 100) / 3
        mvc.perform(get("/api/public/chefs/" + k.tandoor.chefId())).andExpect(jsonPath("$.hygiene.score").value(90.6));
        client.send(GET, base, k.owner, null).andExpect(jsonPath("$.shownPublicly").value(true));

        client.send(PUT, "/api/admin/hygiene/settings", admin, Map.of("publicScoresEnabled", false));
        mvc.perform(get("/api/restaurants/" + slug)).andExpect(jsonPath("$.hygiene").doesNotExist());
        mvc.perform(get("/api/public/chefs/" + k.tandoor.chefId())).andExpect(jsonPath("$.hygiene").doesNotExist());

        OrderTestKitchen other = new OrderTestKitchen(client);
        client.send(GET, base, other.owner, null).andExpect(status().isNotFound());
        client.send(GET, base, k.tandoor.chefToken(), null).andExpect(status().isForbidden());
    }

    @Test
    void ownersDisputeAndAdminsOverride() throws Exception {
        String base = "/api/owner/restaurants/" + k.restaurantId + "/hygiene";
        scored(100.0, 100.0, 100.0);
        UUID bad = scored(20.0, 40.0, null); // 30.0
        assertThat(client.body(client.send(GET, base, k.owner, null)).path("score").asDouble()).isEqualTo(65.0);

        client.send(POST, base + "/results/" + bad + "/disputes", k.owner, Map.of("reason", "short")).andExpect(status().isBadRequest());
        client.send(POST, base + "/results/" + bad + "/disputes", k.owner, Map.of("reason", "The chef is wearing gloves; the lighting fooled it"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"));
        client.send(POST, base + "/results/" + bad + "/disputes", k.owner, Map.of("reason", "Second dispute of the same result"))
                .andExpect(status().isConflict());
        OrderTestKitchen other = new OrderTestKitchen(client);
        client.send(POST, "/api/owner/restaurants/" + other.restaurantId + "/hygiene/results/" + bad + "/disputes", other.owner,
                Map.of("reason", "Not my restaurant's result at all")).andExpect(status().isNotFound());

        String admin = client.adminToken();
        JsonNode queue = client.body(client.send(GET, "/api/admin/hygiene/disputes", admin, null).andExpect(status().isOk()));
        JsonNode mine = null;
        for (JsonNode d : queue) {
            if (d.path("result").path("id").asText().equals(bad.toString())) mine = d;
        }
        assertThat(mine).isNotNull();
        assertThat(mine.path("result").path("orderId").asText()).isNotBlank(); // admin can watch it via /api/admin/clips/{orderId}/playback
        String disputeId = mine.path("dispute").path("id").asText();

        client.send(POST, "/api/admin/hygiene/disputes/" + disputeId + "/resolve", admin, Map.of("decision", "UPHOLD")).andExpect(status().isBadRequest());
        client.send(POST, "/api/admin/hygiene/disputes/" + disputeId + "/resolve", admin,
                        Map.of("decision", "UPHOLD", "overrideScore", 95, "note", "Gloves visible on review"))
                .andExpect(jsonPath("$.dispute.status").value("UPHELD"))
                .andExpect(jsonPath("$.result.effectiveScore").value(95.0))
                .andExpect(jsonPath("$.result.score").value(30.0));
        client.send(GET, base, k.owner, null).andExpect(jsonPath("$.score").value(97.5))
                .andExpect(jsonPath("$.worst[0].dispute.status").value("UPHELD"));
        client.send(POST, "/api/admin/hygiene/disputes/" + disputeId + "/resolve", admin, Map.of("decision", "REJECT")).andExpect(status().isConflict());

        // Excluding drops a result from every average; rejecting leaves it alone.
        UUID another = scored(10.0, null, null);
        client.send(POST, base + "/results/" + another + "/disputes", k.owner, Map.of("reason", "The camera was pointing at the wall"));
        String second = client.body(client.send(GET, "/api/admin/hygiene/disputes", admin, null)).get(0).path("dispute").path("id").asText();
        client.send(POST, "/api/admin/hygiene/disputes/" + second + "/resolve", admin, Map.of("decision", "UPHOLD", "exclude", true))
                .andExpect(jsonPath("$.result.excluded").value(true)).andExpect(jsonPath("$.result.effectiveScore").doesNotExist());
        client.send(GET, base, k.owner, null).andExpect(jsonPath("$.clipCount").value(2));
        client.send(GET, "/api/admin/hygiene/disputes?status=UPHELD", admin, null).andExpect(jsonPath("$.length()").value(2));
        client.send(GET, "/api/admin/hygiene/disputes", k.owner, null).andExpect(status().isForbidden());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** An order cooked at Tandoor whose clip is READY with a small stand-in video in storage. */
    private UUID readyClip() throws Exception {
        String orderId = k.visibleOrder("TAKEAWAY");
        k.advance(orderId, "ACCEPTED", "PREPARING");
        k.chefSets(orderId, Map.of("status", "READY"));
        String key = "clips/" + k.restaurantId + "/" + orderId + ".mp4";
        storage.uploadClip(key, Files.write(temp.resolve(orderId + ".mp4"), "fake mp4".getBytes()), "video/mp4");
        jdbc.update("""
                update clips set status = 'READY', video_key = ?, thumb_key = ?, duration_s = 42.5, chef_id = ?::uuid,
                       ready_at = now(), expires_at = now() + interval '30 days' where order_id = ?::uuid
                """, key, key, k.tandoor.chefId(), orderId);
        jdbc.update("update orders set clip_status = 'READY' where id = ?::uuid", orderId);
        return jdbc.queryForObject("select id from clips where order_id = ?::uuid", UUID.class, orderId);
    }

    /** What ClipLifecycle does on READY: the event inside a transaction; the hygiene job is queued after commit. */
    private void ready(UUID clipId) {
        tx.executeWithoutResult(s -> events.publishEvent(new ClipReadyEvent(clipId)));
    }

    /** A clip analysed with these item percentages (through the real job). */
    private UUID scored(Double gloves, Double headCover, Double mask) throws Exception {
        UUID clipId = readyClip();
        ready(clipId);
        AI.body = FakeAiService.response(gloves, headCover, mask, List.of(3.0, 9.0, 15.0));
        assertThat(worker.runOnce(WORKER, List.of(HygieneJob.TYPE))).isTrue();
        return jdbc.queryForObject("select id from hygiene_results where clip_id = ?", UUID.class, clipId);
    }

    private String jobStatus(UUID clipId) {
        return jdbc.queryForObject("select status from jobs where dedupe_key = ? order by created_at desc limit 1", String.class, "hygiene:" + clipId);
    }
}
