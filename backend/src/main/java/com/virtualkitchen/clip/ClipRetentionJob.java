package com.virtualkitchen.clip;

import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.order.ClipStatus;
import com.virtualkitchen.order.OrderClips;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Retention: raw footage goes after a successful render (in the job) or after raw-retention at the
 * latest; processed clips go after the retention period (default 30 days) and become EXPIRED.
 * Safe to run on several instances (deletes and state changes are idempotent).
 */
@Component
public class ClipRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ClipRetentionJob.class);

    private final ClipRequestRepository requests;
    private final ClipRepository clips;
    private final OrderClips orderClips;
    private final ObjectStorage storage;
    private final ClipProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ClipRetentionJob(ClipRequestRepository requests, ClipRepository clips, OrderClips orderClips, ObjectStorage storage,
                            ClipProperties properties, PlatformTransactionManager transactionManager, Clock clock) {
        this.requests = requests;
        this.clips = clips;
        this.orderClips = orderClips;
        this.storage = storage;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void run() {
        purgeOldRawFootage();
        expireClips();
    }

    public int purgeOldRawFootage() {
        Instant cutoff = clock.instant().minus(properties.rawRetention());
        List<ClipRequest> old = tx.execute(s -> requests.findWithRawFootageOlderThan(cutoff));
        int purged = 0;
        for (ClipRequest request : old == null ? List.<ClipRequest>of() : old) {
            if (!request.getChunkKeys().isEmpty()) {
                storage.deleteClips(request.getChunkKeys());
            }
            UUID id = request.getId();
            tx.executeWithoutResult(s -> requests.findById(id).ifPresent(r -> r.markRawDeleted(clock.instant())));
            purged++;
        }
        if (purged > 0) {
            log.info("Deleted raw footage of {} order(s) older than {}", purged, properties.rawRetention());
        }
        return purged;
    }

    public int expireClips() {
        List<Clip> due = tx.execute(s -> clips.findByStatusAndExpiresAtBefore(ClipState.READY, clock.instant()));
        int expired = 0;
        for (Clip clip : due == null ? List.<Clip>of() : due) {
            storage.deleteClips(List.of(clip.getVideoKey(), clip.getThumbKey()));
            UUID orderId = clip.getOrderId();
            tx.executeWithoutResult(s -> clips.lockByOrderId(orderId).ifPresent(c -> {
                if (c.getStatus() == ClipState.READY) {
                    c.markExpired(clock.instant());
                    orderClips.setClipStatus(orderId, ClipStatus.EXPIRED, false);
                }
            }));
            expired++;
        }
        if (expired > 0) {
            log.info("Expired {} clip(s)", expired);
        }
        return expired;
    }
}
