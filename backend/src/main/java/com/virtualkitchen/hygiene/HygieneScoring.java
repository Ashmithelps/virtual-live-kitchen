package com.virtualkitchen.hygiene;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.stream.Stream;

/** How one clip's item compliance becomes its 0–100 score. */
final class HygieneScoring {

    private HygieneScoring() {
    }

    /**
     * The mean of the item percentages that could be judged (gloves, head cover, mask), to one decimal. Null when
     * none could: such a clip says nothing about hygiene and is left out of averages.
     */
    static BigDecimal score(Double gloves, Double headCover, Double mask) {
        double[] judged = Stream.of(gloves, headCover, mask).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        if (judged.length == 0) {
            return null;
        }
        double mean = java.util.Arrays.stream(judged).average().orElseThrow();
        return BigDecimal.valueOf(mean).setScale(1, RoundingMode.HALF_UP);
    }
}
