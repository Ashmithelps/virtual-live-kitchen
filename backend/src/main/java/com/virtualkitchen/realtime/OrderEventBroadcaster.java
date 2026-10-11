package com.virtualkitchen.realtime;

import com.virtualkitchen.order.OrderChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes order changes to subscribers once the change has committed (a rolled-back change is
 * never announced). The restaurant topic only gets orders the restaurant may see (paid or
 * pay-later), so unpaid online orders stay invisible to the kitchen, as on the REST API.
 */
@Component
public class OrderEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(OrderEventBroadcaster.class);

    private final SimpMessagingTemplate messaging;

    public OrderEventBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /**
     * Runs before any other after-commit work of the same transaction (e.g. sending a refund),
     * so subscribers always get this transaction's snapshot before anything that follows it.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onOrderChanged(OrderChangedEvent event) {
        OrderEventMessage message = OrderEventMessage.updated(event.order());
        try {
            messaging.convertAndSend(Topics.order(event.order().id()), message);
            if (event.visibleToRestaurant()) {
                messaging.convertAndSend(Topics.restaurantOrders(event.order().restaurantId()), message);
            }
        } catch (RuntimeException e) {
            // Clients recover by refetching on reconnect; never fail the business operation.
            log.warn("Could not broadcast order {}: {}", event.order().id(), e.getMessage());
        }
    }
}
