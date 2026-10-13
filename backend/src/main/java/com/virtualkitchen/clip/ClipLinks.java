package com.virtualkitchen.clip;

import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.order.OrderClipLinks;
import com.virtualkitchen.order.dto.ClipView;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Short-lived signed links to READY clips, for order responses and pushes. */
@Component
public class ClipLinks implements OrderClipLinks {

    private final ClipRepository clips;
    private final ObjectStorage storage;

    public ClipLinks(ClipRepository clips, ObjectStorage storage) {
        this.clips = clips;
        this.storage = storage;
    }

    @Override
    public Map<UUID, ClipView> forOrders(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return clips.findByOrderIdIn(orderIds).stream()
                .filter(c -> c.getStatus() == ClipState.READY && c.getVideoKey() != null)
                .collect(Collectors.toMap(Clip::getOrderId, Function.identity()))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    Clip c = e.getValue();
                    return new ClipView(c.getDurationSeconds().doubleValue(), storage.clipDownloadUrl(c.getVideoKey()),
                            storage.clipDownloadUrl(c.getThumbKey()), c.getExpiresAt());
                }));
    }
}
