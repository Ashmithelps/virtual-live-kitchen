package com.virtualkitchen.kitchen;

import com.virtualkitchen.clip.ClipProperties;
import com.virtualkitchen.common.config.WebProperties;
import com.virtualkitchen.common.crypto.SecureTokens;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.kitchen.chef.Chef;
import com.virtualkitchen.kitchen.chef.ChefRepository;
import com.virtualkitchen.kitchen.consent.ChefConsentService;
import com.virtualkitchen.kitchen.dto.AssignedChef;
import com.virtualkitchen.kitchen.dto.DeviceTokenResponse;
import com.virtualkitchen.kitchen.dto.PairingResponse;
import com.virtualkitchen.kitchen.dto.StationRequest;
import com.virtualkitchen.kitchen.dto.StationResponse;
import com.virtualkitchen.restaurant.RestaurantAccess;
import com.virtualkitchen.restaurant.table.QrCodes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KitchenStationService {

    private static final int DEVICE_TOKEN_BYTES = 32;

    private final KitchenStationRepository stations;
    private final StationAssignmentRepository assignments;
    private final ChefRepository chefs;
    private final ChefConsentService consentService;
    private final RestaurantAccess restaurantAccess;
    private final Clock clock;
    private final WebProperties web;
    private final ClipProperties clipProperties;

    public KitchenStationService(KitchenStationRepository stations, StationAssignmentRepository assignments,
                                 ChefRepository chefs, ChefConsentService consentService,
                                 RestaurantAccess restaurantAccess, Clock clock, WebProperties web,
                                 ClipProperties clipProperties) {
        this.stations = stations;
        this.assignments = assignments;
        this.chefs = chefs;
        this.consentService = consentService;
        this.restaurantAccess = restaurantAccess;
        this.clock = clock;
        this.web = web;
        this.clipProperties = clipProperties;
    }

    @Transactional(readOnly = true)
    public List<StationResponse> list(UUID ownerId, UUID restaurantId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        Map<UUID, List<AssignedChef>> chefsByStation = assignedChefsByStation(restaurantId);
        return stations.findByRestaurantIdOrderByNameAsc(restaurantId).stream()
                .map(s -> toResponse(s, chefsByStation.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @Transactional
    public StationResponse create(UUID ownerId, UUID restaurantId, StationRequest request) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        String name = request.name().strip();
        requireFreeName(restaurantId, name);
        return toResponse(stations.saveAndFlush(new KitchenStation(restaurantId, name, clock.instant())), List.of());
    }

    @Transactional
    public StationResponse rename(UUID ownerId, UUID restaurantId, UUID stationId, StationRequest request) {
        KitchenStation station = requireStation(ownerId, restaurantId, stationId);
        String name = request.name().strip();
        if (!name.equalsIgnoreCase(station.getName())) {
            requireFreeName(restaurantId, name);
        }
        station.rename(name);
        return toResponse(station, assignedChefsByStation(restaurantId).getOrDefault(stationId, List.of()));
    }

    /** Assignments are removed with the station (ON DELETE CASCADE). */
    @Transactional
    public void delete(UUID ownerId, UUID restaurantId, UUID stationId) {
        stations.delete(requireStation(ownerId, restaurantId, stationId));
    }

    /**
     * One-time pairing code for the station phone, shown as a QR on the owner's screen. Using it
     * issues a fresh device token and unpairs any previous phone. Expires after a few minutes.
     */
    @Transactional
    public PairingResponse startPairing(UUID ownerId, UUID restaurantId, UUID stationId) {
        KitchenStation station = requireStation(ownerId, restaurantId, stationId);
        String code = SecureTokens.random(24);
        Instant expiresAt = clock.instant().plus(clipProperties.pairingCodeTtl());
        station.startPairing(SecureTokens.sha256Hex(code), expiresAt);
        // The code travels in the URL fragment, which browsers never send to any server.
        String url = web.base() + "/station#pair=" + code;
        return new PairingResponse(station.getId(), station.getName(), url, QrCodes.svgDataUri(url), expiresAt);
    }

    @Transactional
    public DeviceTokenResponse issueDeviceToken(UUID ownerId, UUID restaurantId, UUID stationId) {
        KitchenStation station = requireStation(ownerId, restaurantId, stationId);
        String token = SecureTokens.random(DEVICE_TOKEN_BYTES);
        station.replaceDeviceTokenHash(SecureTokens.sha256Hex(token));
        return new DeviceTokenResponse(station.getId(), token);
    }

    /**
     * A chef can only be put on a station while they hold the current recording consent. The chef
     * row is locked so a concurrent consent withdrawal cannot slip in between check and insert.
     */
    @Transactional
    public StationResponse assignChef(UUID ownerId, UUID restaurantId, UUID stationId, UUID chefId) {
        KitchenStation station = requireStation(ownerId, restaurantId, stationId);
        Chef chef = chefs.findByIdAndRestaurantIdAndRemovedAtIsNull(chefId, restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef not found"));
        chefs.lockById(chef.getId());
        if (!consentService.hasValidConsent(chef.getId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Chef has not accepted the current recording consent and cannot be assigned to a station");
        }
        StationAssignment.Key key = new StationAssignment.Key(stationId, chefId);
        if (!assignments.existsById(key)) {
            assignments.save(new StationAssignment(stationId, chefId, restaurantId, clock.instant()));
        }
        assignments.flush();
        return toResponse(station, assignedChefsByStation(restaurantId).getOrDefault(stationId, List.of()));
    }

    @Transactional
    public StationResponse unassignChef(UUID ownerId, UUID restaurantId, UUID stationId, UUID chefId) {
        KitchenStation station = requireStation(ownerId, restaurantId, stationId);
        assignments.deleteById(new StationAssignment.Key(stationId, chefId));
        assignments.flush();
        return toResponse(station, assignedChefsByStation(restaurantId).getOrDefault(stationId, List.of()));
    }

    private Map<UUID, List<AssignedChef>> assignedChefsByStation(UUID restaurantId) {
        Map<UUID, Chef> chefById = chefs.findByRestaurantIdAndRemovedAtIsNullOrderByDisplayNameAsc(restaurantId).stream()
                .collect(Collectors.toMap(Chef::getId, Function.identity()));
        return assignments.findByRestaurantId(restaurantId).stream()
                .filter(a -> chefById.containsKey(a.getChefId()))
                .collect(Collectors.groupingBy(StationAssignment::getStationId, Collectors.mapping(
                        a -> new AssignedChef(a.getChefId(), chefById.get(a.getChefId()).getDisplayName()),
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparing(AssignedChef::displayName)).toList()))));
    }

    private KitchenStation requireStation(UUID ownerId, UUID restaurantId, UUID stationId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return stations.findByIdAndRestaurantId(stationId, restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Station not found"));
    }

    private void requireFreeName(UUID restaurantId, String name) {
        if (stations.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A station named '" + name + "' already exists");
        }
    }

    private static StationResponse toResponse(KitchenStation station, List<AssignedChef> chefs) {
        return new StationResponse(station.getId(), station.getName(), station.getDeviceTokenHash() != null,
                station.getDevicePairedAt(), station.getDeviceLastSeenAt(), chefs, station.getCreatedAt());
    }
}
