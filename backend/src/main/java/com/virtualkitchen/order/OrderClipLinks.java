package com.virtualkitchen.order;

import com.virtualkitchen.order.dto.ClipView;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Provided by the clip module: watchable links for READY clips (keeps order → clip one-way). */
public interface OrderClipLinks {

    Map<UUID, ClipView> forOrders(Collection<UUID> orderIds);
}
