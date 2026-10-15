package com.virtualkitchen.hygiene;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HygieneScoringTest {

    @Test
    void scoreIsTheMeanOfTheItemsThatCouldBeJudged() {
        assertThat(HygieneScoring.score(100.0, 50.0, 75.0)).isEqualByComparingTo("75.0");
        assertThat(HygieneScoring.score(90.0, null, null)).isEqualByComparingTo("90.0");
        assertThat(HygieneScoring.score(66.7, 100.0, null)).isEqualByComparingTo("83.4"); // 83.35 rounds half up
        assertThat(HygieneScoring.score(null, null, null)).isNull();
        assertThat(HygieneScoring.score(0.0, 0.0, 0.0)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
