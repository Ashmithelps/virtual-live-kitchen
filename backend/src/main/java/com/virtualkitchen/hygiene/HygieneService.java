package com.virtualkitchen.hygiene;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualkitchen.clip.ClipProperties;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.hygiene.PublicHygieneScore;
import com.virtualkitchen.common.hygiene.PublicHygieneScores;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.hygiene.dto.AdminDisputeResponse;
import com.virtualkitchen.hygiene.dto.HygieneDisputeResponse;
import com.virtualkitchen.hygiene.dto.HygieneOverviewResponse;
import com.virtualkitchen.hygiene.dto.HygieneResultResponse;
import com.virtualkitchen.hygiene.dto.HygieneSettingsResponse;
import com.virtualkitchen.hygiene.dto.ResolveDisputeRequest;
import com.virtualkitchen.restaurant.RestaurantAccess;
import com.virtualkitchen.restaurant.RestaurantProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Hygiene scores from stored results. A clip's result is its newest row; its effective score is the admin override,
 * or nothing if an admin excluded it, else the model's score. Rolling scores average effective scores of clips that
 * became READY within {@code app.hygiene.window}. Public display needs the platform switch on and at least
 * {@code app.hygiene.min-public-clips} scored clips.
 */
@Service
public class HygieneService implements PublicHygieneScores {

    static final String PUBLIC_SETTING = "hygiene.public_scores_enabled";
    private static final int WORST = 10;
    private static final int TREND_WEEKS = 12;

    /** Newest result per clip of one restaurant with its effective score. Needs :restaurantId. */
    private static final String LATEST = """
            WITH latest AS (
                SELECT DISTINCT ON (r.clip_id) r.*, c.ready_at, c.order_id, c.thumb_key, c.status AS clip_status,
                       CASE WHEN r.excluded THEN NULL ELSE coalesce(r.override_score, r.score) END AS effective
                FROM hygiene_results r JOIN clips c ON c.id = r.clip_id
                WHERE r.restaurant_id = :restaurantId
                ORDER BY r.clip_id, r.created_at DESC)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RestaurantAccess restaurants;
    private final HygieneProperties properties;
    private final RestaurantProperties restaurantProperties;
    private final ClipProperties clipProperties;
    private final ObjectStorage storage;
    private final ObjectMapper json;
    private final Clock clock;

    public HygieneService(NamedParameterJdbcTemplate jdbc, RestaurantAccess restaurants, HygieneProperties properties,
                          RestaurantProperties restaurantProperties, ClipProperties clipProperties, ObjectStorage storage,
                          ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.restaurants = restaurants;
        this.properties = properties;
        this.restaurantProperties = restaurantProperties;
        this.clipProperties = clipProperties;
        this.storage = storage;
        this.json = json;
        this.clock = clock;
    }

    // ── public ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicHygieneScore> forRestaurant(UUID restaurantId) {
        if (!publicScoresEnabled()) return Optional.empty();
        return publicIfEnough(rolling(restaurantId, null));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicHygieneScore> forChef(UUID chefId) {
        if (!publicScoresEnabled()) return Optional.empty();
        UUID restaurantId = jdbc.query("SELECT restaurant_id FROM chefs WHERE id = :id", Map.of("id", chefId),
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        return restaurantId == null ? Optional.empty() : publicIfEnough(rolling(restaurantId, chefId));
    }

    // ── owner ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public HygieneOverviewResponse overview(UUID ownerId, UUID restaurantId) {
        restaurants.requireOwned(restaurantId, ownerId);
        boolean enabled = publicScoresEnabled();
        Rolling total = rolling(restaurantId, null);
        MapSqlParameterSource params = params(restaurantId);

        List<HygieneOverviewResponse.TrendPoint> trend = jdbc.query(LATEST + """
                SELECT date_trunc('week', ready_at AT TIME ZONE :zone)::date AS week, avg(effective) AS score, count(effective) AS clips
                FROM latest WHERE ready_at >= :trendSince
                GROUP BY week ORDER BY week
                """, params.addValue("zone", restaurantProperties.timezone().getId())
                .addValue("trendSince", Timestamp.from(clock.instant().minus(java.time.Duration.ofDays(7L * TREND_WEEKS)))),
                (rs, i) -> new HygieneOverviewResponse.TrendPoint(rs.getObject("week", LocalDate.class), round(rs.getBigDecimal("score")), rs.getLong("clips")));

        List<HygieneOverviewResponse.ChefScore> chefs = jdbc.query(LATEST + """
                SELECT ch.id, ch.display_name, avg(l.effective) AS score, count(l.effective) AS clips
                FROM latest l JOIN chefs ch ON ch.id = l.chef_id
                WHERE l.ready_at >= :since
                GROUP BY ch.id, ch.display_name ORDER BY ch.display_name
                """, params, (rs, i) -> {
            long clips = rs.getLong("clips");
            return new HygieneOverviewResponse.ChefScore(rs.getObject("id", UUID.class), rs.getString("display_name"),
                    round(rs.getBigDecimal("score")), clips, enabled && clips >= properties.minPublicClips());
        });

        List<HygieneResultResponse> worst = jdbc.query(LATEST + RESULT_SELECT + """
                WHERE l.ready_at >= :since AND l.effective IS NOT NULL
                ORDER BY l.effective ASC, l.ready_at DESC LIMIT :limit
                """, params.addValue("limit", WORST), this::result);

        return new HygieneOverviewResponse(total.score(), total.clips(), properties.windowDays(), properties.minPublicClips(), enabled,
                enabled && total.clips() >= properties.minPublicClips(), trend, chefs, worst);
    }

    @Transactional
    public HygieneDisputeResponse dispute(UUID ownerId, UUID restaurantId, UUID resultId, String reason) {
        restaurants.requireOwned(restaurantId, ownerId);
        Boolean ours = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM hygiene_results WHERE id = :id AND restaurant_id = :restaurantId)",
                params(restaurantId).addValue("id", resultId), Boolean.class);
        if (!Boolean.TRUE.equals(ours)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Hygiene result not found");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO hygiene_disputes (id, result_id, restaurant_id, opened_by, reason, status, created_at)
                    VALUES (:id, :resultId, :restaurantId, :ownerId, :reason, 'OPEN', :now)
                    """, params(restaurantId).addValue("id", id).addValue("resultId", resultId).addValue("ownerId", ownerId)
                    .addValue("reason", reason.strip()).addValue("now", Timestamp.from(clock.instant())));
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "This result already has an open dispute");
        }
        return dispute(id).orElseThrow();
    }

    // ── admin ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminDisputeResponse> disputes(String status) {
        return jdbc.query("""
                SELECT d.id, d.restaurant_id, d.result_id, r.name
                FROM hygiene_disputes d JOIN restaurants r ON r.id = d.restaurant_id
                WHERE d.status = :status ORDER BY d.created_at
                """, Map.of("status", status), (rs, i) -> new Object[]{rs.getObject("id", UUID.class), rs.getObject("restaurant_id", UUID.class),
                rs.getObject("result_id", UUID.class), rs.getString("name")})
                .stream()
                .map(row -> new AdminDisputeResponse(dispute((UUID) row[0]).orElseThrow(), (UUID) row[1], (String) row[3],
                        resultById((UUID) row[1], (UUID) row[2])))
                .toList();
    }

    @Transactional
    public AdminDisputeResponse resolve(UUID adminId, UUID disputeId, ResolveDisputeRequest request) {
        Map<String, Object> d = jdbc.query("SELECT * FROM hygiene_disputes WHERE id = :id FOR UPDATE", Map.of("id", disputeId),
                rs -> rs.next() ? Map.<String, Object>of("result_id", rs.getObject("result_id", UUID.class), "restaurant_id",
                        rs.getObject("restaurant_id", UUID.class), "status", rs.getString("status")) : null);
        if (d == null) throw new ApiException(HttpStatus.NOT_FOUND, "Dispute not found");
        if (!"OPEN".equals(d.get("status"))) throw new ApiException(HttpStatus.CONFLICT, "This dispute was already resolved");
        Instant now = clock.instant();
        boolean uphold = request.decision() == ResolveDisputeRequest.Decision.UPHOLD;
        boolean exclude = Boolean.TRUE.equals(request.exclude());
        if (uphold && request.overrideScore() == null && !exclude) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Upholding needs a corrected score or exclude = true");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        if (uphold) {
            jdbc.update("""
                    UPDATE hygiene_results SET override_score = :score, excluded = :exclude, override_note = :note,
                           overridden_by = :admin, overridden_at = :now WHERE id = :id
                    """, new MapSqlParameterSource("score", exclude ? null : BigDecimal.valueOf(request.overrideScore()).setScale(1, java.math.RoundingMode.HALF_UP))
                    .addValue("exclude", exclude).addValue("note", note).addValue("admin", adminId).addValue("now", Timestamp.from(now))
                    .addValue("id", d.get("result_id")));
        }
        jdbc.update("""
                UPDATE hygiene_disputes SET status = :status, admin_note = :note, resolved_by = :admin, resolved_at = :now WHERE id = :id
                """, new MapSqlParameterSource("status", uphold ? "UPHELD" : "REJECTED").addValue("note", note).addValue("admin", adminId)
                .addValue("now", Timestamp.from(now)).addValue("id", disputeId));
        UUID restaurantId = (UUID) d.get("restaurant_id");
        String name = jdbc.queryForObject("SELECT name FROM restaurants WHERE id = :id", Map.of("id", restaurantId), String.class);
        return new AdminDisputeResponse(dispute(disputeId).orElseThrow(), restaurantId, name, resultById(restaurantId, (UUID) d.get("result_id")));
    }

    @Transactional(readOnly = true)
    public HygieneSettingsResponse settings() {
        return new HygieneSettingsResponse(publicScoresEnabled(), properties.minPublicClips(), properties.windowDays(), properties.enabled());
    }

    @Transactional
    public HygieneSettingsResponse updateSettings(UUID adminId, boolean publicScoresEnabled) {
        jdbc.update("""
                INSERT INTO platform_settings (key, value, updated_by, updated_at) VALUES (:key, :value, :admin, :now)
                ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at
                """, new MapSqlParameterSource("key", PUBLIC_SETTING).addValue("value", Boolean.toString(publicScoresEnabled))
                .addValue("admin", adminId).addValue("now", Timestamp.from(clock.instant())));
        return settings();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Rolling(Double score, long clips) {
    }

    private Rolling rolling(UUID restaurantId, UUID chefId) {
        return jdbc.queryForObject(LATEST + """
                SELECT avg(effective) AS score, count(effective) AS clips FROM latest
                WHERE ready_at >= :since AND (CAST(:chefId AS uuid) IS NULL OR chef_id = :chefId)
                """, params(restaurantId).addValue("chefId", chefId), (rs, i) -> new Rolling(round(rs.getBigDecimal("score")), rs.getLong("clips")));
    }

    private Optional<PublicHygieneScore> publicIfEnough(Rolling r) {
        return r.score() != null && r.clips() >= properties.minPublicClips()
                ? Optional.of(new PublicHygieneScore(r.score(), r.clips(), properties.windowDays()))
                : Optional.empty();
    }

    boolean publicScoresEnabled() {
        String value = jdbc.query("SELECT value FROM platform_settings WHERE key = :key", Map.of("key", PUBLIC_SETTING),
                rs -> rs.next() ? rs.getString(1) : null);
        return Boolean.parseBoolean(value);
    }

    private MapSqlParameterSource params(UUID restaurantId) {
        return new MapSqlParameterSource("restaurantId", restaurantId)
                .addValue("since", Timestamp.from(clock.instant().minus(properties.window())));
    }

    private static final String RESULT_SELECT = """
            SELECT l.id, l.order_id, o.display_number, ch.display_name AS chef_name, l.ready_at, l.model_version, l.person_frames,
                   l.gloves_pct, l.head_cover_pct, l.mask_pct, l.score, l.effective, l.excluded, l.override_score, l.override_note,
                   l.violations::text AS violations, l.thumb_key, l.clip_status,
                   (SELECT d.id FROM hygiene_disputes d WHERE d.result_id = l.id ORDER BY d.created_at DESC LIMIT 1) AS dispute_id
            FROM latest l JOIN orders o ON o.id = l.order_id LEFT JOIN chefs ch ON ch.id = l.chef_id
            """;

    private HygieneResultResponse resultById(UUID restaurantId, UUID resultId) {
        // Not necessarily the clip's newest result (a dispute names a specific row), so query the row itself.
        String sql = """
                WITH latest AS (
                    SELECT r.*, c.ready_at, c.order_id, c.thumb_key, c.status AS clip_status,
                           CASE WHEN r.excluded THEN NULL ELSE coalesce(r.override_score, r.score) END AS effective
                    FROM hygiene_results r JOIN clips c ON c.id = r.clip_id
                    WHERE r.restaurant_id = :restaurantId AND r.id = :id)
                """ + RESULT_SELECT;
        return jdbc.queryForObject(sql, params(restaurantId).addValue("id", resultId), this::result);
    }

    private HygieneResultResponse result(ResultSet rs, int i) throws SQLException {
        Map<String, List<Double>> violations;
        try {
            violations = json.readValue(rs.getString("violations"), new TypeReference<>() { });
        } catch (IOException e) {
            violations = Map.of();
        }
        Timestamp ready = rs.getTimestamp("ready_at");
        UUID disputeId = rs.getObject("dispute_id", UUID.class);
        boolean watchable = "READY".equals(rs.getString("clip_status"));
        return new HygieneResultResponse(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), rs.getInt("display_number"),
                rs.getString("chef_name"), ready == null ? null : ready.toInstant(), rs.getString("model_version"), rs.getInt("person_frames"),
                dbl(rs.getBigDecimal("gloves_pct")), dbl(rs.getBigDecimal("head_cover_pct")), dbl(rs.getBigDecimal("mask_pct")),
                dbl(rs.getBigDecimal("score")), dbl(rs.getBigDecimal("effective")), rs.getBoolean("excluded"),
                dbl(rs.getBigDecimal("override_score")), rs.getString("override_note"), violations,
                watchable ? storage.clipUrl(rs.getString("thumb_key"), clipProperties.watchUrlTtl()) : null,
                disputeId == null ? null : dispute(disputeId).orElse(null));
    }

    private Optional<HygieneDisputeResponse> dispute(UUID id) {
        return Optional.ofNullable(jdbc.query("SELECT * FROM hygiene_disputes WHERE id = :id", Map.of("id", id), rs -> rs.next()
                ? new HygieneDisputeResponse(rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("reason"), rs.getString("admin_note"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant())
                : null));
    }

    private static Double dbl(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static Double round(BigDecimal value) {
        return value == null ? null : value.setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
