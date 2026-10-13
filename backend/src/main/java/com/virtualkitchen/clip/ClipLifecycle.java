package com.virtualkitchen.clip;

import com.virtualkitchen.kitchen.chef.ChefRepository;
import com.virtualkitchen.order.ClipStatus;
import com.virtualkitchen.order.Order;
import com.virtualkitchen.order.OrderClips;
import com.virtualkitchen.order.OrderRepository;
import com.virtualkitchen.restaurant.RestaurantProperties;
import com.virtualkitchen.restaurant.RestaurantRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Database side of clip processing, in short transactions (the worker never holds one while
 * downloading, running FFmpeg or uploading). Keeps clips.status and orders.clip_status in step.
 */
@Component
public class ClipLifecycle {

    private static final Pattern CHUNK_NAME = Pattern.compile("/(\\d+)-[^/]*\\.(webm|mp4)$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final ClipRepository clips;
    private final ClipRequestRepository requests;
    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final ChefRepository chefs;
    private final OrderClips orderClips;
    private final ClipProperties properties;
    private final RestaurantProperties restaurantProperties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ClipLifecycle(ClipRepository clips, ClipRequestRepository requests, OrderRepository orders,
                         RestaurantRepository restaurants, ChefRepository chefs, OrderClips orderClips,
                         ClipProperties properties, RestaurantProperties restaurantProperties,
                         PlatformTransactionManager transactionManager, Clock clock) {
        this.clips = clips;
        this.requests = requests;
        this.orders = orders;
        this.restaurants = restaurants;
        this.chefs = chefs;
        this.orderClips = orderClips;
        this.properties = properties;
        this.restaurantProperties = restaurantProperties;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public record Chunk(String key, long startMs, String extension) {
    }

    /** Everything the worker needs, captured in one read. */
    public record Work(UUID restaurantId, String restaurantName, String logoKey, int displayNumber, String chefName, String date,
                       List<Chunk> chunks, double offsetS, double windowS) {
    }

    /** Marks the clip PROCESSING and returns the work; empty if there's nothing (more) to do. */
    public Optional<Work> startProcessing(UUID orderId) {
        return Optional.ofNullable(tx.execute(status -> {
            ClipRequest request = requests.findByOrderId(orderId).orElse(null);
            Clip clip = clips.lockByOrderId(orderId).orElse(null);
            if (request == null || clip == null || request.getStatus() != ClipRequestStatus.UPLOADED) {
                return null;
            }
            if (clip.getStatus() != ClipState.PENDING && clip.getStatus() != ClipState.PROCESSING && clip.getStatus() != ClipState.FAILED) {
                return null; // READY / EXPIRED / NOT_AVAILABLE: done already
            }
            List<Chunk> chunks = request.getChunkKeys().stream().map(ClipLifecycle::chunk)
                    .sorted(Comparator.comparingLong(Chunk::startMs)).toList();
            if (chunks.isEmpty()) {
                return null;
            }
            Order order = orders.findById(orderId).orElseThrow();
            var restaurant = restaurants.findById(order.getRestaurantId()).orElseThrow();
            String chefName = order.getPreparedByChefId() == null ? null
                    : chefs.findById(order.getPreparedByChefId()).map(c -> c.getDisplayName()).orElse(null);
            long windowStart = request.getWindowStart().toEpochMilli();
            long footageStart = Math.max(windowStart, chunks.getFirst().startMs());
            double offsetS = (footageStart - chunks.getFirst().startMs()) / 1000.0;
            double windowS = (request.getWindowEnd().toEpochMilli() - footageStart) / 1000.0;
            Instant when = order.getReadyAt() != null ? order.getReadyAt() : order.getPlacedAt();

            clip.moveTo(ClipState.PROCESSING, null, clock.instant());
            orderClips.setClipStatus(orderId, ClipStatus.PROCESSING, true);
            return new Work(restaurant.getId(), restaurant.getName(), restaurant.getLogoKey(), order.getDisplayNumber(), chefName,
                    DATE.format(when.atZone(restaurantProperties.timezone())), chunks, offsetS, windowS);
        }));
    }

    /** READY: the order's watchers get the clip links pushed. */
    public void finish(UUID orderId, String videoKey, String thumbKey, double durationS) {
        tx.executeWithoutResult(status -> {
            Instant now = clock.instant();
            Clip clip = clips.lockByOrderId(orderId).orElseThrow();
            clip.markReady(videoKey, thumbKey, durationS, now.plus(properties.retention()), now);
            orderClips.setClipStatus(orderId, ClipStatus.READY, true);
        });
    }

    public void noFootage(UUID orderId) {
        setState(orderId, ClipState.NOT_AVAILABLE, ClipStatus.NOT_AVAILABLE, "NO_FOOTAGE");
    }

    public void failed(UUID orderId, String reason) {
        setState(orderId, ClipState.FAILED, ClipStatus.FAILED, reason);
    }

    public void rawDeleted(UUID orderId) {
        tx.executeWithoutResult(status -> requests.findByOrderId(orderId).ifPresent(r -> r.markRawDeleted(clock.instant())));
    }

    private void setState(UUID orderId, ClipState state, ClipStatus orderStatus, String reason) {
        tx.executeWithoutResult(status -> clips.lockByOrderId(orderId).ifPresent(clip -> {
            if (clip.getStatus() == ClipState.READY || clip.getStatus() == ClipState.EXPIRED) {
                return;
            }
            clip.moveTo(state, reason, clock.instant());
            orderClips.setClipStatus(orderId, orderStatus, true);
        }));
    }

    static Chunk chunk(String key) {
        Matcher m = CHUNK_NAME.matcher(key);
        if (!m.find()) {
            throw new IllegalStateException("Unexpected chunk key " + key);
        }
        return new Chunk(key, Long.parseLong(m.group(1)), m.group(2));
    }

    Duration rawRetention() {
        return properties.rawRetention();
    }
}
