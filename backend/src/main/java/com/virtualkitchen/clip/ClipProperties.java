package com.virtualkitchen.clip;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.clips")
public record ClipProperties(
        @NotNull Duration windowLead,
        @NotNull Duration windowTail,
        @NotNull Duration uploadTimeout,
        @Min(1) long maxChunkBytes,
        @NotNull Duration pairingCodeTtl,
        @NotNull Duration retention,
        @NotNull Duration rawRetention,
        @NotBlank String ffmpegPath,
        @NotBlank String ffprobePath,
        @NotNull Duration ffmpegTimeout,
        @NotBlank String watermark) {
}
