package com.virtualkitchen.common.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotNull URI endpoint,
        @NotBlank String region,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String mediaBucket,
        @NotBlank String clipsBucket,
        @NotNull Duration uploadUrlTtl,
        @NotNull Duration downloadUrlTtl,
        boolean createBuckets) {

    @Override
    public String toString() {
        return "StorageProperties[endpoint=" + endpoint + ", mediaBucket=" + mediaBucket + ", clipsBucket=" + clipsBucket + ", secretKey=***]";
    }
}
