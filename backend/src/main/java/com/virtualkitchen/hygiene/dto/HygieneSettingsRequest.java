package com.virtualkitchen.hygiene.dto;

import jakarta.validation.constraints.NotNull;

public record HygieneSettingsRequest(@NotNull Boolean publicScoresEnabled) {
}
