package com.virtualkitchen.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Call after changing an order (inside the transaction); listeners run after commit. */
@Component
class OrderEvents {

    private final ApplicationEventPublisher publisher;
    private final OrderMapper mapper;

    OrderEvents(ApplicationEventPublisher publisher, OrderMapper mapper) {
        this.publisher = publisher;
        this.mapper = mapper;
    }

    void changed(Order order) {
        publisher.publishEvent(new OrderChangedEvent(mapper.toResponse(order), order.isVisibleToRestaurant()));
    }
}
