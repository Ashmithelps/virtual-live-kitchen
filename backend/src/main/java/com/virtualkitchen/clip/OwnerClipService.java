package com.virtualkitchen.clip;

import com.virtualkitchen.clip.render.ClipProcessingJob;
import com.virtualkitchen.clip.dto.OwnerClipResponse;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.jobs.JobQueue;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.common.web.PageResponse;
import com.virtualkitchen.order.ClipStatus;
import com.virtualkitchen.order.OrderClips;
import com.virtualkitchen.restaurant.RestaurantAccess;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The owner's view of their restaurant's clips, and re-rendering FAILED ones. */
@Service
public class OwnerClipService {

    private static final String FROM = """
            FROM clips c
            JOIN orders o ON o.id = c.order_id
            LEFT JOIN chefs ch ON ch.id = c.chef_id
            LEFT JOIN kitchen_stations s ON s.id = c.station_id
            LEFT JOIN clip_requests r ON r.order_id = c.order_id
            WHERE c.restaurant_id = :restaurantId AND (CAST(:status AS text) IS NULL OR c.status = :status)
              AND (CAST(:orderId AS uuid) IS NULL OR c.order_id = :orderId)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RestaurantAccess access;
    private final ClipRepository clips;
    private final ClipRequestRepository requests;
    private final OrderClips orderClips;
    private final JobQueue jobs;
    private final ObjectStorage storage;
    private final Clock clock;

    public OwnerClipService(NamedParameterJdbcTemplate jdbc, RestaurantAccess access, ClipRepository clips,
                            ClipRequestRepository requests, OrderClips orderClips, JobQueue jobs, ObjectStorage storage, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clips = clips;
        this.requests = requests;
        this.orderClips = orderClips;
        this.jobs = jobs;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OwnerClipResponse> list(UUID ownerId, UUID restaurantId, ClipState status, int page, int size) {
        access.requireOwned(restaurantId, ownerId);
        MapSqlParameterSource params = params(restaurantId, status, null).addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) " + FROM, params, Long.class);
        return PageResponse.of(query(params), page, size, total == null ? 0 : total);
    }

    private List<OwnerClipResponse> query(MapSqlParameterSource params) {
        return jdbc.query("""
                SELECT c.order_id, o.display_number, o.business_date, c.status, c.failure_reason, c.duration_s,
                       c.video_key, c.thumb_key, ch.display_name AS chef_name, s.name AS station_name,
                       (cardinality(r.chunk_keys) > 0 AND r.raw_deleted_at IS NULL) AS has_raw,
                       c.created_at, c.ready_at, c.expires_at
                """ + FROM + " ORDER BY c.created_at DESC LIMIT :limit OFFSET :offset", params, this::row);
    }

    private static MapSqlParameterSource params(UUID restaurantId, ClipState status, UUID orderId) {
        return new MapSqlParameterSource()
                .addValue("restaurantId", restaurantId)
                .addValue("status", status == null ? null : status.name())
                .addValue("orderId", orderId);
    }

    /** Puts a FAILED clip back in the render queue (its raw footage must still be stored). */
    @Transactional
    public OwnerClipResponse retry(UUID ownerId, UUID restaurantId, UUID orderId) {
        access.requireOwned(restaurantId, ownerId);
        Clip clip = clips.lockByOrderId(orderId)
                .filter(c -> c.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Clip not found"));
        if (clip.getStatus() != ClipState.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only FAILED clips can be retried (this one is " + clip.getStatus() + ")");
        }
        boolean hasRaw = requests.findByOrderId(orderId)
                .map(r -> r.getRawDeletedAt() == null && !r.getChunkKeys().isEmpty())
                .orElse(false);
        if (!hasRaw) {
            throw new ApiException(HttpStatus.CONFLICT, "The raw footage for this order has already been deleted");
        }
        clip.moveTo(ClipState.PENDING, null, clock.instant());
        orderClips.setClipStatus(orderId, ClipStatus.UPLOADED, true);
        jobs.enqueue(ClipProcessingJob.TYPE, Map.of("orderId", orderId.toString()), "clip:" + orderId);
        clips.flush();
        return query(params(restaurantId, null, orderId).addValue("limit", 1).addValue("offset", 0)).getFirst();
    }

    private OwnerClipResponse row(ResultSet rs, int rowNum) throws SQLException {
        ClipState status = ClipState.valueOf(rs.getString("status"));
        boolean ready = status == ClipState.READY;
        BigDecimal duration = rs.getBigDecimal("duration_s");
        String videoKey = rs.getString("video_key");
        String thumbKey = rs.getString("thumb_key");
        return new OwnerClipResponse(
                rs.getObject("order_id", UUID.class),
                rs.getInt("display_number"),
                rs.getObject("business_date", java.time.LocalDate.class),
                status,
                rs.getString("failure_reason"),
                duration == null ? null : duration.doubleValue(),
                ready && videoKey != null ? storage.clipDownloadUrl(videoKey) : null,
                ready && thumbKey != null ? storage.clipDownloadUrl(thumbKey) : null,
                rs.getString("chef_name"),
                rs.getString("station_name"),
                status == ClipState.FAILED && rs.getBoolean("has_raw"),
                instant(rs, "created_at"),
                instant(rs, "ready_at"),
                instant(rs, "expires_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
