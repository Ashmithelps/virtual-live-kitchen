package com.virtualkitchen.hygiene;

import com.virtualkitchen.clip.ClipReadyEvent;
import com.virtualkitchen.common.jobs.JobQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * Queues hygiene analysis for each READY clip, after the clip's transaction has committed and in a transaction of its
 * own: nothing here can delay or undo the clip (or the order flow), and a failure only leaves the clip unscored.
 */
@Component
class HygieneScheduler {

    private static final Logger log = LoggerFactory.getLogger(HygieneScheduler.class);

    private final HygieneProperties properties;
    private final JobQueue jobs;
    private final TransactionTemplate tx;

    HygieneScheduler(HygieneProperties properties, JobQueue jobs, PlatformTransactionManager transactions) {
        this.properties = properties;
        this.jobs = jobs;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void clipReady(ClipReadyEvent event) {
        if (!properties.enabled()) {
            return;
        }
        try {
            tx.executeWithoutResult(s -> jobs.enqueue(HygieneJob.TYPE, Map.of("clipId", event.clipId().toString()), "hygiene:" + event.clipId()));
        } catch (RuntimeException e) {
            log.warn("Couldn't queue hygiene analysis for clip {}; it stays unscored: {}", event.clipId(), e.getMessage());
        }
    }
}
