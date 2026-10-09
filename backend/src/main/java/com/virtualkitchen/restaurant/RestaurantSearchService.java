package com.virtualkitchen.restaurant;

import com.virtualkitchen.common.web.PageResponse;
import com.virtualkitchen.restaurant.dto.RestaurantSummaryResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Time;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-side query for the public restaurant listing (plain SQL: arrays + "open now" are awkward in JPQL). */
@Service
public class RestaurantSearchService {

    /** Mirrors {@link OpeningSlot#isOpenAt}: today's slots, plus the after-midnight tail of yesterday's. */
    static final String OPEN_NOW_SQL = """
            EXISTS (SELECT 1 FROM restaurant_opening_hours h
                    WHERE h.restaurant_id = r.id AND (
                        (h.day_of_week = :today AND h.opens_at <= :localTime
                            AND (h.closes_at <= h.opens_at OR :localTime < h.closes_at))
                     OR (h.day_of_week = :yesterday AND h.closes_at <= h.opens_at AND :localTime < h.closes_at)))
            """;

    private static final String PURE_VEG_SQL =
            "NOT EXISTS (SELECT 1 FROM menu_items mi WHERE mi.restaurant_id = r.id AND NOT mi.is_veg)";

    private final NamedParameterJdbcTemplate jdbc;
    private final RestaurantProperties properties;
    private final Clock clock;

    public RestaurantSearchService(NamedParameterJdbcTemplate jdbc, RestaurantProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    public record Filter(String city, String cuisine, boolean vegOnly, boolean openNow, OrderType orderType) {
    }

    @Transactional(readOnly = true)
    public PageResponse<RestaurantSummaryResponse> search(Filter filter, int page, int size) {
        LocalDateTime localNow = LocalDateTime.now(clock.withZone(properties.timezone()));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("city", filter.city().strip().toLowerCase(Locale.ROOT))
                .addValue("today", localNow.getDayOfWeek().name())
                .addValue("yesterday", localNow.getDayOfWeek().minus(1).name())
                .addValue("localTime", Time.valueOf(localNow.toLocalTime().withNano(0)))
                .addValue("limit", size)
                .addValue("offset", (long) page * size);

        List<String> conditions = new ArrayList<>(List.of("r.status = 'ACTIVE'", "lower(r.city) = :city"));
        if (filter.cuisine() != null && !filter.cuisine().isBlank()) {
            conditions.add(":cuisine = ANY (r.cuisine_tags)");
            params.addValue("cuisine", RestaurantService.normalizeTag(filter.cuisine()));
        }
        if (filter.vegOnly()) {
            conditions.add(PURE_VEG_SQL);
        }
        if (filter.openNow()) {
            conditions.add(OPEN_NOW_SQL);
        }
        if (filter.orderType() != null) {
            conditions.add(switch (filter.orderType()) {
                case DINE_IN -> "r.supports_dine_in";
                case TAKEAWAY -> "r.supports_takeaway";
                case DELIVERY -> "r.supports_delivery";
            });
        }
        String where = " FROM restaurants r WHERE " + String.join(" AND ", conditions);

        Long total = jdbc.queryForObject("SELECT count(*)" + where, params, Long.class);
        List<RestaurantSummaryResponse> items = jdbc.query("""
                SELECT r.slug, r.name, r.address, r.city, r.latitude, r.longitude, r.cuisine_tags,
                       r.supports_dine_in, r.supports_takeaway, r.supports_delivery, r.delivery_radius_km,
                       %s AS pure_veg, %s AS open_now
                """.formatted(PURE_VEG_SQL, OPEN_NOW_SQL) + where + " ORDER BY r.name, r.id LIMIT :limit OFFSET :offset",
                params, ROW_MAPPER);
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    private static final RowMapper<RestaurantSummaryResponse> ROW_MAPPER = (rs, rowNum) -> {
        Set<OrderType> orderTypes = EnumSet.noneOf(OrderType.class);
        if (rs.getBoolean("supports_dine_in")) orderTypes.add(OrderType.DINE_IN);
        if (rs.getBoolean("supports_takeaway")) orderTypes.add(OrderType.TAKEAWAY);
        if (rs.getBoolean("supports_delivery")) orderTypes.add(OrderType.DELIVERY);
        Array tags = rs.getArray("cuisine_tags");
        double radius = rs.getDouble("delivery_radius_km");
        return new RestaurantSummaryResponse(
                rs.getString("slug"), rs.getString("name"), rs.getString("address"), rs.getString("city"),
                rs.getDouble("latitude"), rs.getDouble("longitude"),
                tags == null ? List.of() : List.of((String[]) tags.getArray()),
                orderTypes, rs.wasNull() ? null : radius,
                rs.getBoolean("pure_veg"), rs.getBoolean("open_now"));
    };
}
