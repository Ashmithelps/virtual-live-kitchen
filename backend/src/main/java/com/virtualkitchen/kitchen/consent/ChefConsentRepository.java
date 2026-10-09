package com.virtualkitchen.kitchen.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChefConsentRepository extends JpaRepository<ChefConsent, UUID> {

    Optional<ChefConsent> findFirstByChefIdAndConsentVersionAndWithdrawnAtIsNullOrderByAcceptedAtDesc(
            UUID chefId, String consentVersion);

    List<ChefConsent> findByChefIdAndWithdrawnAtIsNull(UUID chefId);

    List<ChefConsent> findByChefIdInAndConsentVersionAndWithdrawnAtIsNull(Collection<UUID> chefIds, String consentVersion);
}
