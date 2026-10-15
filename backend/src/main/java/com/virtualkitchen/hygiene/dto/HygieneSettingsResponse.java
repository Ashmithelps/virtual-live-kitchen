package com.virtualkitchen.hygiene.dto;

public record HygieneSettingsResponse(boolean publicScoresEnabled, int minPublicClips, int windowDays, boolean analysisEnabled) {
}
