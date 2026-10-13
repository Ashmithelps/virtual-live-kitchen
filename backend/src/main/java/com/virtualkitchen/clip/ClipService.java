package com.virtualkitchen.clip;

import com.virtualkitchen.clip.dto.ChunkUploadRequest;
import com.virtualkitchen.clip.dto.ChunkUploadUrl;
import com.virtualkitchen.clip.dto.ClipRequestResponse;
import com.virtualkitchen.clip.dto.ClipWindowResponse;
import com.virtualkitchen.clip.render.ClipProcessingJob;
import com.virtualkitchen.common.jobs.JobQueue;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.kitchen.KitchenStationRepository;
import com.virtualkitchen.kitchen.consent.ChefConsentService;
import com.virtualkitchen.kitchen.device.StationDevice;
import com.virtualkitchen.order.ClipStatus;
import com.virtualkitchen.order.OrderClips;
import com.virtualkitchen.order.OrderReadyEvent;
import com.virtualkitchen.order.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Kitchen footage requests. When an order becomes READY the station that prepared it is asked
 * for the window [PREPARING − lead, READY + tail], but only if the chef holds valid recording
 * consent at that moment and the station has a paired phone; otherwise the order's clip is
 * NOT_AVAILABLE and no window is ever sent.
 */
@Service
public class ClipService {

    private static final Logger log = LoggerFactory.getLogger(ClipService.class);
    private static final Map<String, String> EXTENSIONS = Map.of("video/webm", "webm", "video/mp4", "mp4");

    private final ClipRequestRepository requests;
    private final ClipRepository clips;
    private final JobQueue jobs;
    private final KitchenStationRepository stations;
    private final ChefConsentService consent;
    private final OrderClips orderClips;
    private final OrderRepository orders;
    private final ObjectStorage storage;
    private final ClipProperties properties;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public ClipService(ClipRequestRepository requests, ClipRepository clips, JobQueue jobs, KitchenStationRepository stations,
                       ChefConsentService consent,
                       OrderClips orderClips, OrderRepository orders, ObjectStorage storage, ClipProperties properties,
                       ApplicationEventPublisher publisher, Clock clock) {
        this.requests = requests;
        this.clips = clips;
        this.jobs = jobs;
        this.stations = stations;
        this.consent = consent;
        this.orderClips = orderClips;
        this.orders = orders;
        this.storage = storage;
        this.properties = properties;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Runs inside the READY transaction. */
    @EventListener
    public void onOrderReady(OrderReadyEvent ready) {
        Instant now = clock.instant();
        if (requests.findByOrderId(ready.orderId()).isPresent()) {
            return; // READY happens once per order; be safe anyway
        }
        Instant start = ready.preparingAt().minus(properties.windowLead());
        Instant end = ready.readyAt().plus(properties.windowTail());
        ClipRequest request = requests.save(new ClipRequest(ready.orderId(), ready.restaurantId(), ready.stationId(),
                ready.chefId(), start, end, now));
        Clip clip = clips.save(new Clip(ready.orderId(), ready.restaurantId(), ready.chefId(), ready.stationId(), ClipState.PENDING, now));

        NotAvailableReason unavailable = null;
        if (ready.chefId() == null || !consent.hasValidConsent(ready.chefId())) {
            unavailable = NotAvailableReason.NO_CONSENT;
        } else if (ready.stationId() == null || stations.findById(ready.stationId()).map(s -> !s.hasDevice()).orElse(true)) {
            unavailable = NotAvailableReason.NO_DEVICE;
        }
        if (unavailable != null) {
            request.markNotAvailable(unavailable, now);
            clip.moveTo(ClipState.NOT_AVAILABLE, unavailable.name(), now);
            orderClips.setClipStatus(ready.orderId(), ClipStatus.NOT_AVAILABLE, false);
            log.info("No clip for order {}: {}", ready.orderId(), unavailable);
            return;
        }
        orderClips.setClipStatus(ready.orderId(), ClipStatus.PENDING, false);
        publisher.publishEvent(new ClipWindowEvent(ready.stationId(), window(request, ready.displayNumber())));
    }

    @Transactional(readOnly = true)
    public List<ClipWindowResponse> pendingForStation(StationDevice device) {
        return requests.findByStationIdAndStatusOrderByRequestedAtAsc(device.stationId(), ClipRequestStatus.PENDING_UPLOAD).stream()
                .map(r -> window(r, orders.findById(r.getOrderId()).map(o -> o.getDisplayNumber()).orElse(0)))
                .toList();
    }

    /**
     * Presigned PUTs for the phone's chunks. Only for a pending request of this station, only for
     * chunks overlapping its window, and each URL is bound to one key under
     * raw/{restaurantId}/{orderId}/ and to the chunk's exact size.
     */
    @Transactional(readOnly = true)
    public List<ChunkUploadUrl> uploadUrls(StationDevice device, UUID orderId, ChunkUploadRequest request) {
        ClipRequest clip = requireOwnPending(device, orderId);
        return request.chunks().stream().map(chunk -> {
            String extension = EXTENSIONS.get(baseType(chunk.contentType()));
            if (extension == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "contentType must be video/webm or video/mp4");
            }
            if (!chunk.endedAt().isAfter(chunk.startedAt())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Chunk " + chunk.chunkId() + " ends before it starts");
            }
            if (!clip.overlaps(chunk.startedAt(), chunk.endedAt())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Chunk " + chunk.chunkId() + " is outside the order's window");
            }
            if (chunk.sizeBytes() > properties.maxChunkBytes()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Chunk " + chunk.chunkId() + " is larger than allowed");
            }
            String key = prefix(clip) + chunk.startedAt().toEpochMilli() + "-" + chunk.chunkId() + "." + extension;
            ObjectStorage.PresignedUpload upload = storage.presignClipUpload(key, baseType(chunk.contentType()), chunk.sizeBytes());
            return new ChunkUploadUrl(chunk.chunkId(), key, upload.url(), "PUT", upload.headers(), upload.expiresAt());
        }).toList();
    }

    /**
     * The phone finished uploading. Every key must be under this order's prefix and actually exist
     * in storage. An empty list means the phone had no footage for the window. Idempotent.
     */
    @Transactional
    public ClipRequestResponse chunksUploaded(StationDevice device, UUID orderId, List<String> objectKeys) {
        ClipRequest clip = requests.lockByOrderId(orderId)
                .filter(r -> device.stationId().equals(r.getStationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No clip request for this order at this station"));
        if (clip.getStatus() == ClipRequestStatus.NOT_AVAILABLE) {
            throw new ApiException(HttpStatus.CONFLICT, "This clip request is closed (" + clip.getReason() + ")");
        }
        Instant now = clock.instant();
        if (objectKeys.isEmpty()) {
            if (clip.getStatus() == ClipRequestStatus.PENDING_UPLOAD) {
                clip.markNotAvailable(NotAvailableReason.NO_FOOTAGE, now);
                clips.findByOrderId(orderId).ifPresent(c -> c.moveTo(ClipState.NOT_AVAILABLE, NotAvailableReason.NO_FOOTAGE.name(), now));
                orderClips.setClipStatus(orderId, ClipStatus.NOT_AVAILABLE, true);
            }
            return response(clip);
        }
        Pattern allowed = Pattern.compile("^" + Pattern.quote(prefix(clip)) + "[0-9]{1,15}-[A-Za-z0-9_-]{1,64}\\.(webm|mp4)$");
        for (String key : objectKeys) {
            if (!allowed.matcher(key).matches()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Object key is not for this order: " + key);
            }
            if (!storage.clipExists(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Object was not uploaded: " + key);
            }
        }
        boolean first = clip.getStatus() == ClipRequestStatus.PENDING_UPLOAD;
        clip.markUploaded(objectKeys, now);
        if (first) {
            orderClips.setClipStatus(orderId, ClipStatus.UPLOADED, true);
            // Same transaction as the upload confirmation: the job exists if and only if this commits.
            jobs.enqueue(ClipProcessingJob.TYPE, Map.of("orderId", orderId.toString()), "clip:" + orderId);
        }
        return response(clip);
    }

    /** Phones keep 45 minutes of footage, so an old pending request can no longer be fulfilled. */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    @Transactional
    public void expireStaleRequests() {
        Instant now = clock.instant();
        for (ClipRequest stale : requests.findByStatusAndRequestedAtBefore(ClipRequestStatus.PENDING_UPLOAD, now.minus(properties.uploadTimeout()))) {
            stale.markNotAvailable(NotAvailableReason.UPLOAD_TIMEOUT, now);
            clips.findByOrderId(stale.getOrderId()).ifPresent(c -> c.moveTo(ClipState.NOT_AVAILABLE, NotAvailableReason.UPLOAD_TIMEOUT.name(), now));
            orderClips.setClipStatus(stale.getOrderId(), ClipStatus.NOT_AVAILABLE, true);
            log.info("Clip for order {} timed out waiting for the station", stale.getOrderId());
        }
    }

    private ClipRequest requireOwnPending(StationDevice device, UUID orderId) {
        ClipRequest clip = requests.findByOrderId(orderId)
                .filter(r -> device.stationId().equals(r.getStationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No clip request for this order at this station"));
        if (clip.getStatus() == ClipRequestStatus.NOT_AVAILABLE) {
            throw new ApiException(HttpStatus.CONFLICT, "This clip request is closed (" + clip.getReason() + ")");
        }
        return clip;
    }

    /** Uploads are confined to this prefix; nothing else in the bucket is reachable. */
    static String prefix(ClipRequest clip) {
        return "raw/" + clip.getRestaurantId() + "/" + clip.getOrderId() + "/";
    }

    private static String baseType(String contentType) {
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).strip().toLowerCase();
    }

    private static ClipWindowResponse window(ClipRequest r, int displayNumber) {
        return new ClipWindowResponse(r.getId(), r.getOrderId(), displayNumber, r.getWindowStart(), r.getWindowEnd());
    }

    private static ClipRequestResponse response(ClipRequest r) {
        return new ClipRequestResponse(r.getOrderId(), r.getStatus(), r.getReason(), r.getChunkKeys().size());
    }
}
