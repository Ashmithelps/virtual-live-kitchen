package com.virtualkitchen.restaurant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void slugifiesNamesToUrlSafeAscii() {
        assertThat(Slugs.slugify("Café Spice & Co, Bengaluru")).isEqualTo("cafe-spice-co-bengaluru");
        assertThat(Slugs.slugify("  --Dosa   Plaza--  ")).isEqualTo("dosa-plaza");
        assertThat(Slugs.slugify("मसाला")).isEmpty();
        assertThat(Slugs.slugify("a".repeat(200))).hasSizeLessThanOrEqualTo(74).matches(Slugs.REGEX);
    }
}
