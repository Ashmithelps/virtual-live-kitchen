package com.virtualkitchen.kitchen.consent;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.kitchen.StationAssignmentRepository;
import com.virtualkitchen.kitchen.chef.Chef;
import com.virtualkitchen.kitchen.chef.ChefRepository;
import com.virtualkitchen.kitchen.consent.dto.ConsentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recording consent. A chef is recordable only while they hold a non-withdrawn acceptance of the
 * <em>current</em> consent version; bumping the version therefore requires everyone to re-accept.
 */
@Service
public class ChefConsentService {

    private final ChefConsentRepository consents;
    private final ChefRepository chefs;
    private final StationAssignmentRepository assignments;
    private final ConsentDocument document;
    private final Clock clock;

    public ChefConsentService(ChefConsentRepository consents, ChefRepository chefs,
                              StationAssignmentRepository assignments, ConsentDocument document, Clock clock) {
        this.consents = consents;
        this.chefs = chefs;
        this.assignments = assignments;
        this.document = document;
        this.clock = clock;
    }

    /**
     * Gate for station assignment now and for recording/clips in later milestones. Callers that
     * act on the answer should hold the chef row lock ({@link ChefRepository#lockById}).
     */
    @Transactional(readOnly = true)
    public boolean hasValidConsent(UUID chefId) {
        return currentConsent(chefId).isPresent();
    }

    @Transactional(readOnly = true)
    public Map<UUID, ChefConsent> currentConsents(Collection<UUID> chefIds) {
        if (chefIds.isEmpty()) {
            return Map.of();
        }
        return consents.findByChefIdInAndConsentVersionAndWithdrawnAtIsNull(chefIds, document.version()).stream()
                .collect(Collectors.toMap(ChefConsent::getChefId, Function.identity(), (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public ConsentResponse forChefUser(UUID userId) {
        Chef chef = requireChef(userId);
        return response(currentConsent(chef.getId()));
    }

    /** Idempotent. Rejects acceptance of anything other than the exact current text. */
    @Transactional
    public ConsentResponse accept(UUID userId, String version, String textHash, String ip) {
        Chef chef = chefs.lockById(requireChef(userId).getId()).orElseThrow();
        if (!document.version().equals(version) || !document.textHash().equals(textHash)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Consent text has changed; fetch GET /api/chef/consent and accept the current version");
        }
        Optional<ChefConsent> existing = currentConsent(chef.getId());
        if (existing.isPresent()) {
            return response(existing);
        }
        ChefConsent consent = consents.save(new ChefConsent(chef.getId(), version, textHash, clock.instant(), ip));
        return response(Optional.of(consent));
    }

    /**
     * Withdraws every active acceptance and removes the chef from all stations, so they cannot be
     * recorded again until they re-accept and are re-assigned.
     */
    @Transactional
    public ConsentResponse withdraw(UUID userId) {
        Chef chef = chefs.lockById(requireChef(userId).getId()).orElseThrow();
        Instant now = clock.instant();
        consents.findByChefIdAndWithdrawnAtIsNull(chef.getId()).forEach(c -> c.withdraw(now));
        assignments.deleteByChefId(chef.getId());
        return response(Optional.empty());
    }

    private Optional<ChefConsent> currentConsent(UUID chefId) {
        return consents.findFirstByChefIdAndConsentVersionAndWithdrawnAtIsNullOrderByAcceptedAtDesc(
                chefId, document.version());
    }

    private Chef requireChef(UUID userId) {
        return chefs.findByUserIdAndRemovedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef profile not found"));
    }

    private ConsentResponse response(Optional<ChefConsent> consent) {
        return new ConsentResponse(document.version(), document.text(), document.textHash(),
                consent.isPresent(), consent.map(ChefConsent::getAcceptedAt).orElse(null));
    }
}
