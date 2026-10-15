package com.virtualkitchen.admin;

import com.virtualkitchen.admin.dto.MetricsResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only platform metrics (one query per measure, grouped by IST day). */
@Service
public class AdminMetricsService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AdminMetricsService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MetricsResponse metrics(int days) {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        LocalDate from = today.minusDays(days - 1L);
        MapSqlParameterSource p = new MapSqlParameterSource("from", from).addValue("to", today).addValue("zone", IST.getId());

        Map<LocalDate, long[]> rows = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) rows.put(d, new long[6]);
        collect(rows, 0, 1, """
                SELECT business_date AS d, count(*) AS a,
                       coalesce(sum(total_paise) FILTER (WHERE status NOT IN ('REJECTED', 'CANCELLED')), 0) AS b
                FROM orders WHERE business_date BETWEEN :from AND :to GROUP BY business_date
                """, p);
        collect(rows, 2, -1, """
                SELECT (ready_at AT TIME ZONE :zone)::date AS d, count(*) AS a, 0 AS b FROM clips
                WHERE status IN ('READY', 'EXPIRED') AND (ready_at AT TIME ZONE :zone)::date BETWEEN :from AND :to GROUP BY 1
                """, p);
        collect(rows, 3, -1, "SELECT view_date AS d, count(*) AS a, 0 AS b FROM clip_views WHERE view_date BETWEEN :from AND :to GROUP BY 1", p);
        collect(rows, 4, -1, """
                SELECT (created_at AT TIME ZONE :zone)::date AS d, count(*) AS a, 0 AS b FROM clip_shares
                WHERE (created_at AT TIME ZONE :zone)::date BETWEEN :from AND :to GROUP BY 1
                """, p);
        collect(rows, 5, -1, """
                SELECT (paid_at AT TIME ZONE :zone)::date AS d, count(*) AS a, 0 AS b FROM tips
                WHERE status IN ('PAID', 'TRANSFERRED') AND (paid_at AT TIME ZONE :zone)::date BETWEEN :from AND :to GROUP BY 1
                """, p);

        List<MetricsResponse.Day> series = rows.entrySet().stream()
                .map(e -> new MetricsResponse.Day(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3], e.getValue()[4], e.getValue()[5]))
                .toList();
        MetricsResponse.Day totals = new MetricsResponse.Day(null, sum(series, 0), sum(series, 1), sum(series, 2), sum(series, 3), sum(series, 4), sum(series, 5));

        Long active = jdbc.queryForObject("SELECT count(*) FROM restaurants WHERE status = 'ACTIVE'", Map.of(), Long.class);
        Long mrr = jdbc.queryForObject("""
                SELECT coalesce(sum(p.price_paise), 0) FROM restaurant_subscriptions s JOIN plans p ON p.id = s.plan_id WHERE s.status = 'ACTIVE'
                """, Map.of(), Long.class);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("SELECT status, count(*) AS n FROM restaurant_subscriptions GROUP BY status ORDER BY status", Map.of(),
                rs -> { byStatus.put(rs.getString("status"), rs.getLong("n")); });
        return new MetricsResponse(days, series, totals, active == null ? 0 : active, mrr == null ? 0 : mrr, byStatus);
    }

    private void collect(Map<LocalDate, long[]> rows, int a, int b, String sql, MapSqlParameterSource p) {
        jdbc.query(sql, p, rs -> {
            long[] row = rows.get(rs.getObject("d", LocalDate.class));
            if (row != null) {
                row[a] = rs.getLong("a");
                if (b >= 0) row[b] = rs.getLong("b");
            }
        });
    }

    private static long sum(List<MetricsResponse.Day> series, int field) {
        return series.stream().mapToLong(d -> switch (field) {
            case 0 -> d.orders();
            case 1 -> d.gmvPaise();
            case 2 -> d.clips();
            case 3 -> d.clipViews();
            case 4 -> d.shares();
            default -> d.tips();
        }).sum();
    }
}
