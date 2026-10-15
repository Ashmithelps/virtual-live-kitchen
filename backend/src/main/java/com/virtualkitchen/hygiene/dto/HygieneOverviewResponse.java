package com.virtualkitchen.hygiene.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The owner's hygiene page: rolling score, weekly trend, per-chef scores and the worst recent clips. */
public record HygieneOverviewResponse(
        @Schema(nullable = true, description = "Rolling average over windowDays; null = no scored clips") Double score,
        long clipCount,
        int windowDays,
        int minPublicClips,
        @Schema(description = "An admin has turned public hygiene scores on for the platform") boolean publicScoresEnabled,
        @Schema(description = "Customers currently see the score (enabled and at least minPublicClips)") boolean shownPublicly,
        List<TrendPoint> trend,
        List<ChefScore> chefs,
        List<HygieneResultResponse> worst) {

    public record TrendPoint(LocalDate weekStart, @Schema(nullable = true) Double score, long clips) {
    }

    public record ChefScore(UUID chefId, String displayName, @Schema(nullable = true) Double score, long clipCount, boolean shownPublicly) {
    }
}
