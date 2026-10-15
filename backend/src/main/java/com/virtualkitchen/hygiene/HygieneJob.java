package com.virtualkitchen.hygiene;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.clip.Clip;
import com.virtualkitchen.clip.ClipRepository;
import com.virtualkitchen.clip.ClipState;
import com.virtualkitchen.common.jobs.JobHandler;
import com.virtualkitchen.common.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HYGIENE_ANALYZE: sends a READY clip to the AI service and stores the summary. Runs in its own worker lane. A clip
 * that can't be analysed (service down past all retries, unreadable video, no model) simply stays unscored.
 */
@Component
public class HygieneJob implements JobHandler {

    public static final String TYPE = "HYGIENE_ANALYZE";
    static final String LANE = "hygiene";
    private static final Duration VIDEO_LINK_TTL = Duration.ofMinutes(15);
    private static final List<String> ITEMS = List.of("gloves", "headCover", "mask");
    private static final Logger log = LoggerFactory.getLogger(HygieneJob.class);

    private final ClipRepository clips;
    private final ObjectStorage storage;
    private final HygieneClient client;
    private final HygieneProperties properties;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Clock clock;

    HygieneJob(ClipRepository clips, ObjectStorage storage, HygieneClient client, HygieneProperties properties, JdbcTemplate jdbc,
               ObjectMapper json, PlatformTransactionManager transactions, Clock clock) {
        this.clips = clips;
        this.storage = storage;
        this.client = client;
        this.properties = properties;
        this.jdbc = jdbc;
        this.json = json;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String lane() {
        return LANE;
    }

    @Override
    public void handle(JsonNode payload) throws JsonProcessingException {
        UUID clipId = UUID.fromString(payload.path("clipId").asText());
        Optional<Clip> found = tx.execute(s -> clips.findById(clipId).filter(c -> c.getStatus() == ClipState.READY && c.getVideoKey() != null));
        if (found == null || found.isEmpty()) {
            log.info("Clip {} is no longer READY; skipping hygiene analysis", clipId);
            return;
        }
        Clip clip = found.get();
        HygieneClient.Analysis analysis = client.analyze(storage.clipUrlVia(properties.videoS3Endpoint(), clip.getVideoKey(), VIDEO_LINK_TTL));

        Map<String, HygieneClient.Item> items = analysis.summary().items() == null ? Map.of() : analysis.summary().items();
        Double gloves = pct(items.get("gloves"));
        Double headCover = pct(items.get("headCover"));
        Double mask = pct(items.get("mask"));
        BigDecimal score = HygieneScoring.score(gloves, headCover, mask);
        Map<String, List<Double>> violations = new LinkedHashMap<>();
        for (String item : ITEMS) {
            HygieneClient.Item i = items.get(item);
            violations.put(item, i == null || i.violationExamples() == null ? List.of() : i.violationExamples().stream().limit(3).toList());
        }
        int inserted = jdbc.update("""
                INSERT INTO hygiene_results (id, clip_id, restaurant_id, chef_id, model_version, sampled_frames, person_frames,
                                             gloves_pct, head_cover_pct, mask_pct, score, violations, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (clip_id, model_version) DO NOTHING
                """, UUID.randomUUID(), clip.getId(), clip.getRestaurantId(), clip.getChefId(), trim(analysis.modelVersion()),
                analysis.summary().sampledFrames(), analysis.summary().personFrames(), gloves, headCover, mask, score,
                json.writeValueAsString(violations), Timestamp.from(clock.instant()));
        log.info("Hygiene for clip {}: score {} (model {}){}", clip.getId(), score, analysis.modelVersion(), inserted == 0 ? " (already stored)" : "");
    }

    @Override
    public void onExhausted(JsonNode payload, String lastError) {
        log.warn("Hygiene analysis gave up for clip {}; it stays unscored: {}", payload.path("clipId").asText(), lastError);
    }

    private static Double pct(HygieneClient.Item item) {
        if (item == null || item.compliancePct() == null) return null;
        return Math.max(0, Math.min(100, item.compliancePct()));
    }

    private static String trim(String version) {
        return version.length() > 120 ? version.substring(0, 120) : version;
    }
}
