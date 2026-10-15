package com.virtualkitchen.hygiene;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.hygiene")
public record HygieneProperties(
        boolean enabled,
        @NotNull URI serviceUrl,
        String apiKey,
        @NotNull Duration requestTimeout,
        /** Storage endpoint as the AI service reaches it; blank = the backend's own. */
        String videoS3Endpoint,
        @NotNull Duration window,
        @Min(1) int minPublicClips) {

    int windowDays() {
        return (int) Math.max(1, window.toDays());
    }

    @Override
    public String toString() {
        return "HygieneProperties[enabled=" + enabled + ", serviceUrl=" + serviceUrl + ", apiKey=***]";
    }
}
