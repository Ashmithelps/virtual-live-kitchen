package com.virtualkitchen.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionGuardTest {

    private static MockEnvironment good() {
        return new MockEnvironment()
                .withProperty("app.payment.provider", "razorpay")
                .withProperty("app.payment.razorpay.key-id", "rzp_live_abc")
                .withProperty("app.payment.razorpay.webhook-secret", "whsec")
                .withProperty("app.cors.allowed-origins", "https://vlk.in,https://www.vlk.in")
                .withProperty("app.web.base-url", "https://vlk.in")
                .withProperty("app.storage.endpoint", "https://acct.r2.cloudflarestorage.com")
                .withProperty("app.rate-limit.trusted-proxy-secret", "x".repeat(40))
                .withProperty("app.rate-limit.enabled", "true");
    }

    @Test
    void productionSettingsPass() {
        assertThat(ProductionGuard.check(good())).isEmpty();
    }

    @Test
    void developmentSettingsAreRefused() {
        MockEnvironment env = good()
                .withProperty("app.payment.provider", "mock")
                .withProperty("app.cors.allowed-origins", "https://vlk.in,http://localhost:3000")
                .withProperty("app.web.base-url", "http://localhost:3000")
                .withProperty("app.storage.endpoint", "http://localhost:9000")
                .withProperty("app.rate-limit.trusted-proxy-secret", "short")
                .withProperty("app.rate-limit.enabled", "false");
        env.setActiveProfiles("prod", "dev-seed");
        assertThat(ProductionGuard.check(env)).hasSize(7)
                .anyMatch(p -> p.contains("[payment-provider]"))
                .anyMatch(p -> p.contains("[cors-https]"))
                .anyMatch(p -> p.contains("dev-seed"));
    }

    @Test
    void testKeysNeedAnExplicitWaiver() {
        MockEnvironment env = good().withProperty("app.payment.razorpay.key-id", "rzp_test_abc");
        assertThat(ProductionGuard.check(env)).singleElement().asString().contains("[razorpay-test-keys]");
        env.withProperty("PRODUCTION_GUARD_ALLOW", "razorpay-test-keys, storage-https");
        assertThat(ProductionGuard.check(env)).isEmpty();
    }

    @Test
    void devSeedCannotBeWaived() {
        MockEnvironment env = good().withProperty("PRODUCTION_GUARD_ALLOW", "dev-seed");
        env.setActiveProfiles("prod", "dev-seed");
        assertThat(ProductionGuard.check(env)).singleElement().asString().contains("dev-seed");
    }
}
