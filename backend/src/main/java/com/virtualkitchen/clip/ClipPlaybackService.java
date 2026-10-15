package com.virtualkitchen.clip;

import com.virtualkitchen.clip.dto.ClipPlaybackResponse;
import com.virtualkitchen.clip.dto.ClipShareResponse;
import com.virtualkitchen.clip.dto.PublicClipResponse;
import com.virtualkitchen.common.config.WebProperties;
import com.virtualkitchen.common.crypto.SecureTokens;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.kitchen.chef.Chef;
import com.virtualkitchen.kitchen.chef.ChefRepository;
import com.virtualkitchen.order.Order;
import com.virtualkitchen.order.OrderRepository;
import com.virtualkitchen.restaurant.Restaurant;
import com.virtualkitchen.restaurant.RestaurantAccess;
import com.virtualkitchen.restaurant.RestaurantProperties;
import com.virtualkitchen.restaurant.RestaurantRepository;
import com.virtualkitchen.restaurant.RestaurantStatus;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Watching, downloading and sharing READY clips. Watch/download links are presigned for
 * {@code app.clips.watch-url-ttl} and only issued to the order's customer, the restaurant owner and admins.
 * Anyone with an active share link gets the public view; views are counted once per visitor per IST day.
 */
@Service
public class ClipPlaybackService {

    private static final int SHARE_TOKEN_BYTES = 16;

    private final ClipRepository clips;
    private final ClipShareRepository shares;
    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final RestaurantAccess restaurantAccess;
    private final ChefRepository chefs;
    private final ObjectStorage storage;
    private final ClipProperties properties;
    private final RestaurantProperties restaurantProperties;
    private final WebProperties web;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ClipPlaybackService(ClipRepository clips, ClipShareRepository shares, OrderRepository orders,
                               RestaurantRepository restaurants, RestaurantAccess restaurantAccess, ChefRepository chefs,
                               ObjectStorage storage, ClipProperties properties, RestaurantProperties restaurantProperties,
                               WebProperties web, JdbcTemplate jdbc, Clock clock) {
        this.clips = clips;
        this.shares = shares;
        this.orders = orders;
        this.restaurants = restaurants;
        this.restaurantAccess = restaurantAccess;
        this.chefs = chefs;
        this.storage = storage;
        this.properties = properties;
        this.restaurantProperties = restaurantProperties;
        this.web = web;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ── watch / download ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ClipPlaybackResponse forCustomer(UUID customerId, UUID orderId) {
        return playback(customerOrder(customerId, orderId), true);
    }

    @Transactional(readOnly = true)
    public ClipPlaybackResponse forOwner(UUID ownerId, UUID restaurantId, UUID orderId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        Order order = orders.findById(orderId).filter(o -> o.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
        return playback(order, false);
    }

    @Transactional(readOnly = true)
    public ClipPlaybackResponse forAdmin(UUID orderId) {
        return playback(orders.findById(orderId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found")), false);
    }

    // ── share links ──────────────────────────────────────────────────────────

    /** The order's public link: the active one, or a new one. */
    @Transactional
    public ClipShareResponse share(UUID customerId, UUID orderId) {
        Order order = customerOrder(customerId, orderId);
        clips.lockByOrderId(order.getId()); // two taps at once must not create two links
        Clip clip = readyClip(order);
        ClipShare share = shares.findByClipIdAndRevokedAtIsNull(clip.getId())
                .orElseGet(() -> shares.saveAndFlush(new ClipShare(clip.getId(), SecureTokens.random(SHARE_TOKEN_BYTES), customerId, clock.instant())));
        return toResponse(share);
    }

    /** Turns the active link off; anyone opening it afterwards gets "not found". Sharing again makes a new link. */
    @Transactional
    public void revoke(UUID customerId, UUID orderId) {
        Order order = customerOrder(customerId, orderId);
        clips.findByOrderId(order.getId())
                .flatMap(clip -> shares.findByClipIdAndRevokedAtIsNull(clip.getId()))
                .ifPresent(share -> share.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public PublicClipResponse publicClip(String token) {
        ClipShare share = activeShare(token);
        Clip clip = clips.findById(share.getClipId()).orElseThrow();
        requireWatchable(clip);
        Order order = orders.findById(clip.getOrderId()).orElseThrow();
        Restaurant restaurant = restaurants.findById(order.getRestaurantId()).orElseThrow();
        Optional<Chef> chef = chef(order);
        return new PublicClipResponse(
                "Watch my order being made at " + restaurant.getName(),
                restaurant.getName(),
                restaurant.getSlug(),
                restaurant.getCity(),
                restaurant.getStatus() == RestaurantStatus.ACTIVE,
                chef.map(Chef::getId).orElse(null),
                chef.map(Chef::getDisplayName).orElse(null),
                clip.getDurationSeconds().doubleValue(),
                storage.clipUrl(clip.getVideoKey(), properties.watchUrlTtl()),
                storage.clipUrl(clip.getThumbKey(), properties.watchUrlTtl()),
                clip.getReadyAt(),
                clock.instant().plus(properties.watchUrlTtl()));
    }

    /** Counts a view of the share page; the same visitor counts once per IST day. */
    @Transactional
    public void recordView(String token, String visitorId) {
        ClipShare share = activeShare(token);
        Clip clip = clips.findById(share.getClipId()).orElseThrow();
        requireWatchable(clip);
        Instant now = clock.instant();
        LocalDate today = now.atZone(restaurantProperties.timezone()).toLocalDate();
        jdbc.update("""
                INSERT INTO clip_views (clip_id, view_date, visitor_hash, share_id, first_seen_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (clip_id, view_date, visitor_hash) DO NOTHING
                """, clip.getId(), Date.valueOf(today), SecureTokens.sha256Hex(visitorId.strip()), share.getId(), Timestamp.from(now));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ClipPlaybackResponse playback(Order order, boolean includeShare) {
        Clip clip = readyClip(order);
        Restaurant restaurant = restaurants.findById(order.getRestaurantId()).orElseThrow();
        String filename = restaurant.getSlug() + "-order-" + order.getDisplayNumber() + ".mp4";
        ClipShareResponse share = includeShare ? shares.findByClipIdAndRevokedAtIsNull(clip.getId()).map(this::toResponse).orElse(null) : null;
        return new ClipPlaybackResponse(
                order.getId(),
                order.getDisplayNumber(),
                restaurant.getName(),
                chef(order).map(Chef::getDisplayName).orElse(null),
                clip.getDurationSeconds().doubleValue(),
                storage.clipUrl(clip.getVideoKey(), properties.watchUrlTtl()),
                storage.clipUrl(clip.getThumbKey(), properties.watchUrlTtl()),
                storage.clipAttachmentUrl(clip.getVideoKey(), properties.watchUrlTtl(), filename),
                filename,
                clock.instant().plus(properties.watchUrlTtl()),
                clip.getExpiresAt(),
                viewCount(clip.getId()),
                share);
    }

    private Order customerOrder(UUID customerId, UUID orderId) {
        return orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private Clip readyClip(Order order) {
        Clip clip = clips.findByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This order has no kitchen clip"));
        if (clip.getStatus() == ClipState.EXPIRED) {
            throw new ApiException(HttpStatus.GONE, "This clip has expired");
        }
        if (clip.getStatus() != ClipState.READY) {
            throw new ApiException(HttpStatus.CONFLICT, "The clip isn't ready yet (" + clip.getStatus() + ")");
        }
        return clip;
    }

    private ClipShare activeShare(String token) {
        return shares.findByTokenAndRevokedAtIsNull(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This link doesn't exist or was turned off"));
    }

    private static void requireWatchable(Clip clip) {
        if (clip.getStatus() != ClipState.READY) {
            throw new ApiException(HttpStatus.GONE, "This clip is no longer available");
        }
    }

    private Optional<Chef> chef(Order order) {
        return order.getPreparedByChefId() == null ? Optional.empty() : chefs.findById(order.getPreparedByChefId());
    }

    private long viewCount(UUID clipId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM clip_views WHERE clip_id = ?", Long.class, clipId);
        return n == null ? 0 : n;
    }

    private ClipShareResponse toResponse(ClipShare share) {
        return new ClipShareResponse(share.getId(), share.getToken(), web.base() + "/c/" + share.getToken(), share.getCreatedAt());
    }
}
