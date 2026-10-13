package com.virtualkitchen.realtime;

import com.virtualkitchen.clip.ClipWindowEvent;
import com.virtualkitchen.clip.dto.ClipWindowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells a station phone which footage to upload, after the READY change commits. If the phone is
 * offline it misses this push, and picks the window up from GET /api/station-device/clip-requests
 * when it reconnects.
 */
@Component
public class StationWindowBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(StationWindowBroadcaster.class);

    private final SimpMessagingTemplate messaging;

    public StationWindowBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public record ClipWindowMessage(String type, ClipWindowResponse window) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClipWindow(ClipWindowEvent event) {
        try {
            messaging.convertAndSend(Topics.station(event.stationId()), new ClipWindowMessage("CLIP_WINDOW", event.window()));
        } catch (RuntimeException e) {
            log.warn("Could not push clip window for order {}: {}", event.window().orderId(), e.getMessage());
        }
    }
}
