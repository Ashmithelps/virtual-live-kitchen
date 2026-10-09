package com.virtualkitchen.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPropertiesTest {

    private final OrderProperties fivePercent = new OrderProperties(500, 50, 50);

    @Test
    void taxIsRoundedHalfUpInPaise() {
        assertThat(fivePercent.taxOn(59_700)).isEqualTo(2_985);
        assertThat(fivePercent.taxOn(10)).isEqualTo(1);   // 0.5 paise rounds up
        assertThat(fivePercent.taxOn(9)).isEqualTo(0);    // 0.45 paise rounds down
        assertThat(new OrderProperties(0, 1, 1).taxOn(12_345)).isZero();
    }
}
