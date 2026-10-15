package com.virtualkitchen.kitchen.chef;

import com.virtualkitchen.common.hygiene.PublicHygieneScores;
import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserAccountService;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.kitchen.KitchenStationRepository;
import com.virtualkitchen.kitchen.StationAssignmentRepository;
import com.virtualkitchen.kitchen.chef.dto.ChefResponse;
import com.virtualkitchen.kitchen.chef.dto.ChefSelfResponse;
import com.virtualkitchen.kitchen.chef.dto.CreateChefRequest;
import com.virtualkitchen.kitchen.chef.dto.UpdateChefRequest;
import com.virtualkitchen.kitchen.consent.ChefConsent;
import com.virtualkitchen.kitchen.consent.ChefConsentService;
import com.virtualkitchen.kitchen.dto.StationRef;
import com.virtualkitchen.restaurant.Restaurant;
import com.virtualkitchen.restaurant.RestaurantAccess;
import com.virtualkitchen.restaurant.RestaurantRepository;
import com.virtualkitchen.restaurant.media.MediaKeys;
import com.virtualkitchen.restaurant.media.UploadPurpose;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.virtualkitchen.kitchen.chef.dto.PublicChefResponse;
import com.virtualkitchen.restaurant.RestaurantStatus;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChefService {

    private final ChefRepository chefs;
    private final StationAssignmentRepository assignments;
    private final KitchenStationRepository stations;
    private final ChefConsentService consentService;
    private final UserAccountService userAccountService;
    private final RestaurantAccess restaurantAccess;
    private final RestaurantRepository restaurants;
    private final ObjectStorage objectStorage;
    private final ChefStats stats;
    private final PublicHygieneScores hygieneScores;
    private final Clock clock;

    public ChefService(ChefRepository chefs, StationAssignmentRepository assignments, KitchenStationRepository stations,
                       ChefConsentService consentService, UserAccountService userAccountService,
                       RestaurantAccess restaurantAccess, RestaurantRepository restaurants,
                       ObjectStorage objectStorage, ChefStats stats,
                       PublicHygieneScores hygieneScores, Clock clock) {
        this.stats = stats;
        this.hygieneScores = hygieneScores;
        this.chefs = chefs;
        this.assignments = assignments;
        this.stations = stations;
        this.consentService = consentService;
        this.userAccountService = userAccountService;
        this.restaurantAccess = restaurantAccess;
        this.restaurants = restaurants;
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    // ── public ───────────────────────────────────────────────────────────────

    /** Active chefs of live restaurants only; anyone else is "not found". */
    @Transactional(readOnly = true)
    public PublicChefResponse publicProfile(UUID chefId) {
        Chef chef = chefs.findById(chefId).filter(c -> c.getRemovedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef not found"));
        Restaurant restaurant = restaurants.findById(chef.getRestaurantId())
                .filter(r -> r.getStatus() == RestaurantStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef not found"));
        ChefStats.Public counts = stats.forPublic(chef.getId());
        return new PublicChefResponse(chef.getId(), chef.getDisplayName(), objectStorage.downloadUrl(chef.getPhotoKey()),
                chef.getBio(), chef.getSpecialty(), restaurant.getName(), restaurant.getSlug(), restaurant.getCity(),
                counts.clipCount(), counts.totalViews(), counts.tipCount(), counts.followerCount(), counts.tippable(),
                hygieneScores.forChef(chef.getId()).orElse(null));
    }

    // ── owner ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChefResponse> list(UUID ownerId, UUID restaurantId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        List<Chef> list = chefs.findByRestaurantIdAndRemovedAtIsNullOrderByDisplayNameAsc(restaurantId);
        Map<UUID, ChefConsent> consents = consentService.currentConsents(list.stream().map(Chef::getId).toList());
        return list.stream().map(c -> toResponse(c, consents.get(c.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ChefResponse get(UUID ownerId, UUID restaurantId, UUID chefId) {
        Chef chef = requireChef(ownerId, restaurantId, chefId);
        return toResponse(chef, consentService.currentConsents(List.of(chefId)).get(chefId));
    }

    @Transactional
    public ChefResponse create(UUID ownerId, UUID restaurantId, CreateChefRequest request) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        String photoKey = MediaKeys.requireValid(blankToNull(request.photoKey()), restaurantId, UploadPurpose.CHEF_PHOTO);
        User user = userAccountService.createChefUser(request.name(), request.phone(), request.initialPassword());
        Chef chef = new Chef(user.getId(), restaurantId, clock.instant());
        String displayName = blankToNull(request.displayName()) != null ? request.displayName().strip() : user.getName();
        chef.updateProfile(displayName, photoKey, blankToNull(request.bio()), blankToNull(request.specialty()));
        return toResponse(chefs.saveAndFlush(chef), null);
    }

    @Transactional
    public ChefResponse update(UUID ownerId, UUID restaurantId, UUID chefId, UpdateChefRequest request) {
        Chef chef = requireChef(ownerId, restaurantId, chefId);
        String photoKey = MediaKeys.requireValid(blankToNull(request.photoKey()), restaurantId, UploadPurpose.CHEF_PHOTO);
        chef.updateProfile(request.displayName().strip(), photoKey, blankToNull(request.bio()),
                blankToNull(request.specialty()));
        return toResponse(chef, consentService.currentConsents(List.of(chefId)).get(chefId));
    }

    /**
     * Soft delete: takes the chef off every station and disables their login. The chef row and
     * consent history are kept for audit and for clips they already appear in.
     */
    @Transactional
    public void remove(UUID ownerId, UUID restaurantId, UUID chefId) {
        Chef chef = requireChef(ownerId, restaurantId, chefId);
        chefs.lockById(chef.getId());
        assignments.deleteByChefId(chef.getId());
        chef.remove(clock.instant());
        userAccountService.disable(chef.getUserId());
    }

    // ── chef (self) ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChefSelfResponse self(UUID userId) {
        Chef chef = chefs.findByUserIdAndRemovedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef profile not found"));
        Restaurant restaurant = restaurants.findById(chef.getRestaurantId()).orElseThrow();
        List<StationRef> stationRefs = assignments.findByChefId(chef.getId()).stream()
                .map(a -> stations.findById(a.getStationId()).orElseThrow())
                .map(s -> new StationRef(s.getId(), s.getName()))
                .sorted(Comparator.comparing(StationRef::name))
                .toList();
        return new ChefSelfResponse(chef.getId(), chef.getDisplayName(), objectStorage.downloadUrl(chef.getPhotoKey()),
                chef.getBio(), chef.getSpecialty(), restaurant.getId(), restaurant.getName(),
                !consentService.hasValidConsent(chef.getId()), stationRefs);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Chef requireChef(UUID ownerId, UUID restaurantId, UUID chefId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return chefs.findByIdAndRestaurantIdAndRemovedAtIsNull(chefId, restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef not found"));
    }

    private ChefResponse toResponse(Chef chef, ChefConsent consent) {
        User user = userAccountService.getById(chef.getUserId());
        ChefStats.ForOwner owner = stats.forOwner(List.of(chef.getId())).getOrDefault(chef.getId(), new ChefStats.ForOwner(0, "NOT_SET_UP"));
        return new ChefResponse(chef.getId(), chef.getUserId(), user.getPhone(), chef.getDisplayName(),
                chef.getPhotoKey(), objectStorage.downloadUrl(chef.getPhotoKey()), chef.getBio(), chef.getSpecialty(),
                consent != null, consent == null ? null : consent.getAcceptedAt(), chef.getCreatedAt(), owner.tipCount(), owner.payoutStatus());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
