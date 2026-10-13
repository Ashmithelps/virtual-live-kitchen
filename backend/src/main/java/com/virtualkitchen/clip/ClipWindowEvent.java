package com.virtualkitchen.clip;

import com.virtualkitchen.clip.dto.ClipWindowResponse;

import java.util.UUID;

/** A station must upload this window; pushed to /topic/station/{stationId} after commit. */
public record ClipWindowEvent(UUID stationId, ClipWindowResponse window) {
}
