package com.virtualkitchen.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Platform metrics per IST day for the last {@code days} days, plus current totals. Money in paise. */
public record MetricsResponse(
        int days,
        List<Day> series,
        Day totals,
        long activeRestaurants,
        @Schema(description = "Monthly recurring revenue: plan prices (excl. GST) of ACTIVE subscriptions") long mrrPaise,
        Map<String, Long> subscriptionsByStatus) {

    /** {@code gmvPaise}: order totals excluding rejected and cancelled orders. */
    public record Day(@Schema(nullable = true) LocalDate date, long orders, long gmvPaise, long clips, long clipViews, long shares, long tips) {
    }
}
