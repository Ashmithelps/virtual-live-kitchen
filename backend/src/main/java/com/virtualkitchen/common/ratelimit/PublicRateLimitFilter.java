package com.virtualkitchen.common.ratelimit;

import com.virtualkitchen.common.error.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/** Answers 429 (standard error body + Retry-After) when a client exceeds a public endpoint's limit. Runs before security. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final PublicRateLimiter limiter;
    private final ErrorResponseWriter errors;

    public PublicRateLimitFilter(RateLimitProperties properties, PublicRateLimiter limiter, ErrorResponseWriter errors) {
        this.properties = properties;
        this.limiter = limiter;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Optional<String> rule = limiter.ruleFor(request.getMethod(), path);
        if (rule.isPresent()) {
            Duration wait = limiter.tryAcquire(rule.get(), ClientIp.of(request, properties.trustedProxySecret()));
            if (!wait.isZero()) {
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, (wait.toMillis() + 999) / 1000)));
                errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down and try again shortly.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
