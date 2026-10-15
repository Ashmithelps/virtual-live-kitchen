package com.virtualkitchen.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Per-client-IP limits on public (unauthenticated) endpoints. The first rule whose method and path match a request
 * applies; requests matching no rule (signed-in API calls, webhooks) are not limited here.
 *
 * @param trustedProxySecret shared with the web app's server (BACKEND_PROXY_SECRET). Requests carrying it may name
 *                           the real client in {@code X-Client-IP}; without it the TCP peer (after Caddy's
 *                           X-Forwarded-For) is the client. Empty = never trust X-Client-IP.
 */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, String trustedProxySecret, @Min(1000) int maxTrackedClients,
                                  @NotNull @Valid List<Rule> rules) {

    /** {@code capacity} requests per client, refilled evenly over {@code period}. */
    public record Rule(@NotBlank String name, @NotEmpty List<String> methods, @NotEmpty List<String> paths,
                       @Min(1) int capacity, @NotNull Duration period) {
    }
}
