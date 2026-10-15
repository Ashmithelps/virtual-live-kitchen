package com.virtualkitchen.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fails startup under the prod profile when a development-only setting slipped into the environment.
 * {@code PRODUCTION_GUARD_ALLOW} (comma-separated check ids) waives checks for a staging rehearsal only.
 */
@Component
@Profile("prod")
public class ProductionGuard implements InitializingBean {

    private final Environment env;

    public ProductionGuard(Environment env) {
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = check(env);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with the prod profile:\n - " + String.join("\n - ", problems));
        }
    }

    static List<String> check(Environment env) {
        Set<String> allowed = Arrays.stream(env.getProperty("PRODUCTION_GUARD_ALLOW", "").split(","))
                .map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        boolean razorpay = "razorpay".equals(env.getProperty("app.payment.provider"));
        problem(problems, allowed, "payment-provider", !razorpay,
                "PAYMENT_PROVIDER must be razorpay (the mock provider approves every payment)");
        problem(problems, allowed, "razorpay-test-keys", razorpay && env.getProperty("app.payment.razorpay.key-id", "").startsWith("rzp_test_"),
                "RAZORPAY_KEY_ID is a test key");
        problem(problems, allowed, "webhook-secret", razorpay && env.getProperty("app.payment.razorpay.webhook-secret", "").isBlank(),
                "RAZORPAY_WEBHOOK_SECRET is empty");
        String origins = env.getProperty("app.cors.allowed-origins", "");
        problem(problems, allowed, "cors-https", Arrays.stream(origins.split(",")).map(String::strip).anyMatch(o -> !o.startsWith("https://")),
                "CORS_ALLOWED_ORIGINS must list only https:// origins (got " + origins + ")");
        problem(problems, allowed, "web-https", !env.getProperty("app.web.base-url", "").startsWith("https://"),
                "WEB_BASE_URL must be https://");
        problem(problems, allowed, "storage-https", !env.getProperty("app.storage.endpoint", "").startsWith("https://"),
                "S3_ENDPOINT must be https:// (Cloudflare R2)");
        problem(problems, allowed, "proxy-secret", env.getProperty("app.rate-limit.trusted-proxy-secret", "").length() < 32,
                "BACKEND_PROXY_SECRET must be at least 32 characters");
        problem(problems, allowed, "rate-limit", !env.getProperty("app.rate-limit.enabled", Boolean.class, true),
                "RATE_LIMIT_ENABLED must not be false");
        // Never waivable: the dev seed creates accounts with a shared password.
        if (Arrays.asList(env.getActiveProfiles()).contains("dev-seed")) {
            problems.add("the dev-seed profile is active");
        }
        return problems;
    }

    private static void problem(List<String> problems, Set<String> allowed, String id, boolean failed, String message) {
        if (failed && !allowed.contains(id)) {
            problems.add(message + " [" + id + "]");
        }
    }
}
