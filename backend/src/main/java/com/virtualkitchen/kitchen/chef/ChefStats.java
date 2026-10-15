package com.virtualkitchen.kitchen.chef;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Read-only counters about chefs from the clip, tip and follow tables (counts only, never amounts). */
@Component
class ChefStats {

    record Public(long clipCount, long totalViews, long tipCount, long followerCount, boolean tippable) {
    }

    record ForOwner(long tipCount, String payoutStatus) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    ChefStats(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Public forPublic(UUID chefId) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM clips c WHERE c.chef_id = :id AND c.status IN ('READY', 'EXPIRED')) AS clips,
                       (SELECT count(*) FROM clip_views v JOIN clips c ON c.id = v.clip_id WHERE c.chef_id = :id) AS views,
                       (SELECT count(*) FROM tips t WHERE t.chef_id = :id AND t.status IN ('PAID', 'TRANSFERRED')) AS tips,
                       (SELECT count(*) FROM chef_follows f WHERE f.chef_id = :id) AS followers,
                       EXISTS (SELECT 1 FROM chef_payout_accounts p WHERE p.chef_id = :id AND p.status = 'ACTIVE') AS tippable
                """, Map.of("id", chefId), (rs, i) -> new Public(rs.getLong("clips"), rs.getLong("views"), rs.getLong("tips"),
                rs.getLong("followers"), rs.getBoolean("tippable")));
    }

    Map<UUID, ForOwner> forOwner(Collection<UUID> chefIds) {
        Map<UUID, ForOwner> result = new HashMap<>();
        if (chefIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT c.id,
                       (SELECT count(*) FROM tips t WHERE t.chef_id = c.id AND t.status IN ('PAID', 'TRANSFERRED')) AS tips,
                       coalesce((SELECT p.status FROM chef_payout_accounts p WHERE p.chef_id = c.id), 'NOT_SET_UP') AS payout
                FROM chefs c WHERE c.id IN (:ids)
                """, new MapSqlParameterSource("ids", chefIds), rs -> {
            result.put(rs.getObject("id", UUID.class), new ForOwner(rs.getLong("tips"), rs.getString("payout")));
        });
        return result;
    }
}
