package com.virtualkitchen.common.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "app.web")
public record WebProperties(@NotNull URI baseUrl) {

    /** Base URL without a trailing slash, for building links. */
    public String base() {
        String url = baseUrl.toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
