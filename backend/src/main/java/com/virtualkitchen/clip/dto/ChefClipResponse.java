package com.virtualkitchen.clip.dto;

import com.virtualkitchen.clip.ClipState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/** One of the chef's own clips ("My clips"): no watch link; those are for the customer, owner and admin. */
public record ChefClipResponse(int orderDisplayNumber, LocalDate businessDate, ClipState status,
                               @Schema(nullable = true) Double durationSeconds, @Schema(nullable = true) String thumbnailUrl,
                               long viewCount, Instant createdAt, @Schema(nullable = true) Instant readyAt) {
}
