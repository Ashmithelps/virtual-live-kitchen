package com.virtualkitchen.hygiene.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One clip's hygiene result. {@code effectiveScore} is what averages use (after any admin override; null = left out). */
public record HygieneResultResponse(
        UUID id,
        UUID orderId,
        int orderDisplayNumber,
        @Schema(nullable = true) String chefName,
        @Schema(nullable = true) Instant readyAt,
        String modelVersion,
        int personFrames,
        @Schema(nullable = true) Double glovesPct,
        @Schema(nullable = true) Double headCoverPct,
        @Schema(nullable = true) Double maskPct,
        @Schema(nullable = true, description = "As computed from the model") Double score,
        @Schema(nullable = true) Double effectiveScore,
        boolean excluded,
        @Schema(nullable = true) Double overrideScore,
        @Schema(nullable = true) String overrideNote,
        @Schema(description = "Up to 3 timestamps (seconds into the clip) per item: gloves, headCover, mask") Map<String, List<Double>> violations,
        @Schema(nullable = true) String thumbnailUrl,
        @Schema(nullable = true, description = "The latest dispute of this result") HygieneDisputeResponse dispute) {
}
