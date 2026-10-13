package com.virtualkitchen.restaurant;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.menu.MenuService;
import com.virtualkitchen.restaurant.dto.OpeningSlotDto;
import com.virtualkitchen.restaurant.media.MediaKeys;
import com.virtualkitchen.restaurant.media.UploadPurpose;
import com.virtualkitchen.restaurant.dto.RestaurantDetailResponse;
import com.virtualkitchen.restaurant.dto.RestaurantRequest;
import com.virtualkitchen.restaurant.dto.RestaurantResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class RestaurantService {

    private static final int SLUG_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestaurantRepository repository;
    private final RestaurantAccess access;
    private final MenuService menuService;
    private final RestaurantProperties properties;
    private final Clock clock;

    public RestaurantService(RestaurantRepository repository, RestaurantAccess access, MenuService menuService,
                             RestaurantProperties properties, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.menuService = menuService;
        this.properties = properties;
        this.clock = clock;
    }

    // ── owner ────────────────────────────────────────────────────────────────

    @Transactional
    public RestaurantResponse create(UUID ownerId, RestaurantRequest request) {
        Instant now = clock.instant();
        Restaurant restaurant = new Restaurant(ownerId, now);
        String slug = request.slug() != null ? requireFreeSlug(request.slug()) : generateSlug(request);
        apply(restaurant, request, slug, now);
        return RestaurantResponse.from(repository.saveAndFlush(restaurant));
    }

    @Transactional
    public RestaurantResponse update(UUID ownerId, UUID restaurantId, RestaurantRequest request) {
        Restaurant restaurant = access.requireOwned(restaurantId, ownerId);
        String slug = restaurant.getSlug();
        if (request.slug() != null && !request.slug().equals(slug)) {
            slug = requireFreeSlug(request.slug());
        }
        apply(restaurant, request, slug, clock.instant());
        return RestaurantResponse.from(repository.saveAndFlush(restaurant));
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> listOwned(UUID ownerId) {
        return repository.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream().map(RestaurantResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getOwned(UUID ownerId, UUID restaurantId) {
        return RestaurantResponse.from(access.requireOwned(restaurantId, ownerId));
    }

    // ── admin ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RestaurantResponse> listByStatus(RestaurantStatus status) {
        return repository.findByStatusOrderByCreatedAtAsc(status).stream().map(RestaurantResponse::from).toList();
    }

    /** The only way a restaurant becomes ACTIVE. */
    @Transactional
    public RestaurantResponse changeStatusAsAdmin(UUID restaurantId, RestaurantStatus target) {
        Restaurant restaurant = repository.findById(restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Restaurant not found"));
        if (restaurant.getStatus() == target) {
            throw new ApiException(HttpStatus.CONFLICT, "Restaurant is already " + target);
        }
        restaurant.changeStatus(target, clock.instant());
        return RestaurantResponse.from(restaurant);
    }

    // ── public ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RestaurantDetailResponse getPublicBySlug(String slug) {
        Restaurant r = repository.findBySlugAndStatus(slug, RestaurantStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Restaurant not found"));
        LocalDateTime localNow = LocalDateTime.now(clock.withZone(properties.timezone()));
        return new RestaurantDetailResponse(r.getId(), r.getSlug(), r.getName(), r.getAddress(), r.getCity(),
                r.getLatitude(), r.getLongitude(), r.getCuisineTags(),
                r.getOpeningHours().stream().map(OpeningSlotDto::from).toList(),
                r.orderTypes(), r.getDeliveryRadiusKm(),
                OpeningSlot.isOpenAt(r.getOpeningHours(), localNow),
                menuService.customerMenu(r.getId()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void apply(Restaurant restaurant, RestaurantRequest request, String slug, Instant now) {
        boolean delivery = request.orderTypes().contains(OrderType.DELIVERY);
        if (delivery && request.deliveryRadiusKm() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "deliveryRadiusKm is required when DELIVERY is supported");
        }
        List<String> tags = request.cuisineTags() == null ? List.of() : request.cuisineTags().stream()
                .map(RestaurantService::normalizeTag)
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList();
        String logoKey = request.logoKey() == null || request.logoKey().isBlank() ? null : request.logoKey().strip();
        if (logoKey != null) {
            MediaKeys.requireValid(logoKey, restaurant.getId(), UploadPurpose.RESTAURANT_LOGO);
        }
        restaurant.changeLogo(logoKey);
        restaurant.updateDetails(request.name().strip(), slug, request.address().strip(), request.city().strip(),
                request.latitude(), request.longitude(), tags,
                request.openingHours().stream().map(OpeningSlotDto::toSlot).toList(),
                request.orderTypes(), request.deliveryRadiusKm(), now);
    }

    /** "North Indian" → "north-indian", so filters match regardless of how owners typed them. */
    static String normalizeTag(String tag) {
        return Slugs.slugify(Objects.requireNonNull(tag).toLowerCase(Locale.ROOT));
    }

    private String requireFreeSlug(String slug) {
        if (repository.existsBySlug(slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "Slug '" + slug + "' is already taken");
        }
        return slug;
    }

    private String generateSlug(RestaurantRequest request) {
        String base = Slugs.slugify(request.name() + " " + request.city());
        if (base.length() < 3) {
            base = "restaurant";
        }
        if (!repository.existsBySlug(base)) {
            return base;
        }
        for (int i = 0; i < SLUG_ATTEMPTS; i++) {
            String candidate = base + "-" + HexFormat.of().toHexDigits((short) RANDOM.nextInt());
            if (!repository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "Could not generate a unique slug; please provide one");
    }
}
