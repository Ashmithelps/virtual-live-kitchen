package com.virtualkitchen.common.hygiene;

import java.util.Optional;
import java.util.UUID;

/**
 * Provided by the hygiene module. Empty unless an admin has enabled public scores for the platform and the score
 * rests on enough clips; pages must show nothing then (not "no score").
 */
public interface PublicHygieneScores {

    Optional<PublicHygieneScore> forRestaurant(UUID restaurantId);

    Optional<PublicHygieneScore> forChef(UUID chefId);
}
