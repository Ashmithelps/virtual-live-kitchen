package com.virtualkitchen.kitchen.chef.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Owner view of a chef. {@code recordingConsent} = holds the current consent version, not withdrawn. */
public record ChefResponse(
        UUID id,
        UUID userId,
        String phone,
        String displayName,
        @Schema(nullable = true) String photoKey,
        @Schema(nullable = true) String photoUrl,
        @Schema(nullable = true) String bio,
        @Schema(nullable = true) String specialty,
        boolean recordingConsent,
        @Schema(nullable = true) Instant consentAcceptedAt,
        Instant createdAt,
        @Schema(description = "Tips received (count only; amounts are the chef's business)") long tipCount,
        @Schema(description = "NOT_SET_UP, PENDING_KYC or ACTIVE; tips are offered only when ACTIVE") String payoutStatus) {
}
