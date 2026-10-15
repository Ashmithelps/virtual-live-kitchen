package com.virtualkitchen.clip;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.clip.dto.ChefClipResponse;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.common.web.PageResponse;
import com.virtualkitchen.kitchen.chef.ChefRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** "My clips" in the chef app: the clips of orders the chef cooked, newest first, with view counts. */
@RestController
@PreAuthorize("hasRole('CHEF')")
public class ChefClipController {

    private final NamedParameterJdbcTemplate jdbc;
    private final ChefRepository chefs;
    private final ObjectStorage storage;
    private final ClipProperties properties;

    public ChefClipController(NamedParameterJdbcTemplate jdbc, ChefRepository chefs, ObjectStorage storage, ClipProperties properties) {
        this.jdbc = jdbc;
        this.chefs = chefs;
        this.storage = storage;
        this.properties = properties;
    }

    @GetMapping("/api/chef/clips")
    @Transactional(readOnly = true)
    public PageResponse<ChefClipResponse> clips(@AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        UUID chefId = chefs.findByUserIdAndRemovedAtIsNull(user.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef profile not found")).getId();
        MapSqlParameterSource params = new MapSqlParameterSource("chefId", chefId).addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM clips WHERE chef_id = :chefId", params, Long.class);
        List<ChefClipResponse> items = jdbc.query("""
                SELECT o.display_number, o.business_date, c.status, c.duration_s, c.thumb_key, c.created_at, c.ready_at,
                       (SELECT count(*) FROM clip_views v WHERE v.clip_id = c.id) AS views
                FROM clips c JOIN orders o ON o.id = c.order_id
                WHERE c.chef_id = :chefId ORDER BY c.created_at DESC LIMIT :limit OFFSET :offset
                """, params, (rs, i) -> {
            ClipState status = ClipState.valueOf(rs.getString("status"));
            Timestamp ready = rs.getTimestamp("ready_at");
            return new ChefClipResponse(rs.getInt("display_number"), rs.getObject("business_date", LocalDate.class), status,
                    rs.getBigDecimal("duration_s") == null ? null : rs.getBigDecimal("duration_s").doubleValue(),
                    status == ClipState.READY ? storage.clipUrl(rs.getString("thumb_key"), properties.watchUrlTtl()) : null,
                    rs.getLong("views"), rs.getTimestamp("created_at").toInstant(), ready == null ? null : ready.toInstant());
        });
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }
}
