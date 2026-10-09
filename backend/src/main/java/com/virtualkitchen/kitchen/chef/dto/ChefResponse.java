package com.virtualkitchen.kitchen.chef.dto;

import java.time.Instant;
import java.util.UUID;

/** Owner view of a chef. {@code recordingConsent} = holds the current consent version, not withdrawn. */
public record ChefResponse(
        UUID id,
        UUID userId,
        String phone,
        String displayName,
        String photoKey,
        String photoUrl,
        String bio,
        String specialty,
        boolean recordingConsent,
        Instant consentAcceptedAt,
        Instant createdAt) {
}
