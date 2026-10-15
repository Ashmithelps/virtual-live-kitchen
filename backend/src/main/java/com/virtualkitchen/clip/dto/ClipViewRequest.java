package com.virtualkitchen.clip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code visitorId}: a random id the browser keeps; stored only as a hash, for one-view-per-visitor-per-day. */
public record ClipViewRequest(@NotBlank @Size(min = 8, max = 100) String visitorId) {
}
