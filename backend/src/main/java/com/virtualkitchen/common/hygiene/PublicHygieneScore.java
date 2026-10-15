package com.virtualkitchen.common.hygiene;

import io.swagger.v3.oas.annotations.media.Schema;

/** A hygiene score cleared for public display. */
public record PublicHygieneScore(
        @Schema(description = "Rolling average, 0–100") double score,
        @Schema(description = "Scored clips in the window it's based on") long clipCount,
        int windowDays) {
}
