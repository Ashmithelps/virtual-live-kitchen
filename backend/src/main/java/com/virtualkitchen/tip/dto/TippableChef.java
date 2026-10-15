package com.virtualkitchen.tip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** A chef this customer may tip on this order: the one who cooked it, or one they follow at the same restaurant. */
public record TippableChef(UUID chefId, String displayName, @Schema(nullable = true) String photoUrl, boolean cookedThisOrder) {
}
