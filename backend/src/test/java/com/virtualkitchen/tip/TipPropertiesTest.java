package com.virtualkitchen.tip;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TipPropertiesTest {

    @Test
    void platformFeeIsIntegerPaiseRoundedDownSoTheChefNeverGetsLess() {
        assertThat(new TipProperties(0, 1000, 500000, List.of(2000L)).feeOn(5000)).isZero();
        assertThat(new TipProperties(250, 1000, 500000, List.of(2000L)).feeOn(5000)).isEqualTo(125);   // 2.5% of ₹50
        assertThat(new TipProperties(250, 1000, 500000, List.of(2000L)).feeOn(2099)).isEqualTo(52);    // 52.475 → 52
    }
}
