package com.virtualkitchen.common.billing;

import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;

/** Provided by the billing module: what a restaurant's plan allows. Empty = no limit / platform default. */
public interface PlanLimits {

    OptionalInt maxStations(UUID restaurantId);

    java.util.Optional<Duration> clipRetention(UUID restaurantId);
}
