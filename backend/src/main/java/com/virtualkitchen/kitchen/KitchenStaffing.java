package com.virtualkitchen.kitchen;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.kitchen.chef.Chef;
import com.virtualkitchen.kitchen.chef.ChefRepository;
import com.virtualkitchen.kitchen.consent.ChefConsentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Kitchen module API for other modules (orders now, recording later). */
@Component
public class KitchenStaffing {

    private final ChefRepository chefs;
    private final KitchenStationRepository stations;
    private final StationAssignmentRepository assignments;
    private final ChefConsentService consentService;

    public KitchenStaffing(ChefRepository chefs, KitchenStationRepository stations,
                           StationAssignmentRepository assignments, ChefConsentService consentService) {
        this.chefs = chefs;
        this.stations = stations;
        this.assignments = assignments;
        this.consentService = consentService;
    }

    @Transactional(readOnly = true)
    public Chef requireChefByUser(UUID userId) {
        return chefs.findByUserIdAndRemovedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef profile not found"));
    }

    /**
     * Confirms {@code chefId} currently works {@code stationId} in this restaurant and holds valid
     * recording consent. Locks the chef row (in the caller's transaction) so a concurrent consent
     * withdrawal cannot interleave.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRecordableOnStation(UUID restaurantId, UUID stationId, UUID chefId) {
        chefs.findByIdAndRestaurantIdAndRemovedAtIsNull(chefId, restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Chef not found in this restaurant"));
        chefs.lockById(chefId);
        stations.findByIdAndRestaurantId(stationId, restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Station not found in this restaurant"));
        if (!assignments.existsById(new StationAssignment.Key(stationId, chefId))) {
            throw new ApiException(HttpStatus.CONFLICT, "Chef is not assigned to this station");
        }
        if (!consentService.hasValidConsent(chefId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Chef has not accepted the current recording consent");
        }
    }
}
