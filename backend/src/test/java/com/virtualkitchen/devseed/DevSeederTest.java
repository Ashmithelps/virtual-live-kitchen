package com.virtualkitchen.devseed;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevSeederTest {

    @Test
    void refusesProductionProfilesAndWeakPasswords() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("dev-seed", "prod");
        assertThatThrownBy(() -> DevSeeder.requireDevelopment(prod, "long-enough-password"))
                .hasMessageContaining("never run together");

        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("local", "dev-seed");
        assertThatThrownBy(() -> DevSeeder.requireDevelopment(dev, "")).hasMessageContaining("DEV_SEED_PASSWORD");
        assertThatCode(() -> DevSeeder.requireDevelopment(dev, "long-enough-password")).doesNotThrowAnyException();
    }
}
