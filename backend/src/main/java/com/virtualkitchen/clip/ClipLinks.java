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

/** Thumbnail links of READY clips for order responses and pushes (watch links come from ClipPlaybackService). */
@Component
public class ClipLinks implements OrderClipLinks {

    private final ClipRepository clips;
    private final ObjectStorage storage;
    private final ClipProperties properties;

    public ClipLinks(ClipRepository clips, ObjectStorage storage, ClipProperties properties) {
        this.clips = clips;
        this.storage = storage;
        this.properties = properties;
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
                    return new ClipView(c.getDurationSeconds().doubleValue(), storage.clipUrl(c.getThumbKey(), properties.watchUrlTtl()),
                            c.getExpiresAt());
                }));
    }
}
