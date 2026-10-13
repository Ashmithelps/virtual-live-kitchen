package com.virtualkitchen.kitchen;

import com.virtualkitchen.auth.security.AuthenticatedUser;
import com.virtualkitchen.kitchen.dto.DeviceTokenResponse;
import com.virtualkitchen.kitchen.dto.PairingResponse;
import com.virtualkitchen.kitchen.dto.StationRequest;
import com.virtualkitchen.kitchen.dto.StationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/restaurants/{restaurantId}/stations")
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
public class OwnerKitchenStationController {

    private final KitchenStationService service;

    public OwnerKitchenStationController(KitchenStationService service) {
        this.service = service;
    }

    @GetMapping
    public List<StationResponse> list(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId) {
        return service.list(owner.id(), restaurantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StationResponse create(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                  @Valid @RequestBody StationRequest request) {
        return service.create(owner.id(), restaurantId, request);
    }

    @PutMapping("/{stationId}")
    public StationResponse rename(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                  @PathVariable UUID stationId, @Valid @RequestBody StationRequest request) {
        return service.rename(owner.id(), restaurantId, stationId, request);
    }

    @DeleteMapping("/{stationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                       @PathVariable UUID stationId) {
        service.delete(owner.id(), restaurantId, stationId);
    }

    /** Pairing QR for the station's recording phone (one-time code, expires in minutes). */
    @PostMapping("/{stationId}/pairing")
    public PairingResponse startPairing(@AuthenticationPrincipal AuthenticatedUser owner,
                                        @PathVariable UUID restaurantId, @PathVariable UUID stationId) {
        return service.startPairing(owner.id(), restaurantId, stationId);
    }

    /** Returns a new device token once; the previous token stops working. */
    @PostMapping("/{stationId}/device-token")
    public DeviceTokenResponse issueDeviceToken(@AuthenticationPrincipal AuthenticatedUser owner,
                                                @PathVariable UUID restaurantId, @PathVariable UUID stationId) {
        return service.issueDeviceToken(owner.id(), restaurantId, stationId);
    }

    @PutMapping("/{stationId}/chefs/{chefId}")
    public StationResponse assignChef(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                      @PathVariable UUID stationId, @PathVariable UUID chefId) {
        return service.assignChef(owner.id(), restaurantId, stationId, chefId);
    }

    @DeleteMapping("/{stationId}/chefs/{chefId}")
    public StationResponse unassignChef(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable UUID restaurantId,
                                        @PathVariable UUID stationId, @PathVariable UUID chefId) {
        return service.unassignChef(owner.id(), restaurantId, stationId, chefId);
    }
}
