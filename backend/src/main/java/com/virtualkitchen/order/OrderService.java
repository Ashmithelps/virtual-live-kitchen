package com.virtualkitchen.order;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.geo.Geo;
import com.virtualkitchen.common.web.PageResponse;
import com.virtualkitchen.menu.MenuItem;
import com.virtualkitchen.menu.MenuItemRepository;
import com.virtualkitchen.order.dto.DeliveryAddressRequest;
import com.virtualkitchen.order.dto.OrderLineRequest;
import com.virtualkitchen.order.dto.ConfirmPaymentRequest;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.PaymentCheckoutResponse;
import com.virtualkitchen.order.dto.PlaceOrderRequest;
import com.virtualkitchen.restaurant.OpeningSlot;
import com.virtualkitchen.restaurant.OrderType;
import com.virtualkitchen.restaurant.Restaurant;
import com.virtualkitchen.restaurant.RestaurantProperties;
import com.virtualkitchen.restaurant.RestaurantRepository;
import com.virtualkitchen.restaurant.RestaurantStatus;
import com.virtualkitchen.restaurant.table.DiningTable;
import com.virtualkitchen.restaurant.table.TableSessions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Customer side: place, pay, cancel, view own orders. */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final MenuItemRepository menuItems;
    private final TableSessions tableSessions;
    private final DisplayNumberAllocator displayNumbers;
    private final OrderPaymentService payments;
    private final OrderMapper mapper;
    private final OrderEvents events;
    private final OrderProperties orderProperties;
    private final RestaurantProperties restaurantProperties;
    private final Clock clock;

    public OrderService(OrderRepository orders, RestaurantRepository restaurants, MenuItemRepository menuItems,
                        TableSessions tableSessions, DisplayNumberAllocator displayNumbers, OrderPaymentService payments,
                        OrderMapper mapper, OrderEvents events, OrderProperties orderProperties,
                        RestaurantProperties restaurantProperties, Clock clock) {
        this.orders = orders;
        this.restaurants = restaurants;
        this.menuItems = menuItems;
        this.tableSessions = tableSessions;
        this.displayNumbers = displayNumbers;
        this.payments = payments;
        this.mapper = mapper;
        this.events = events;
        this.orderProperties = orderProperties;
        this.restaurantProperties = restaurantProperties;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse place(UUID customerId, PlaceOrderRequest request) {
        Instant now = clock.instant();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, restaurantProperties.timezone());

        Restaurant restaurant = restaurants.findById(request.restaurantId())
                .filter(r -> r.getStatus() == RestaurantStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Restaurant not found"));
        OrderType type = request.orderType();
        if (!restaurant.orderTypes().contains(type)) {
            throw new ApiException(HttpStatus.CONFLICT, restaurant.getName() + " does not offer " + type + " orders");
        }
        if (!OpeningSlot.isOpenAt(restaurant.getOpeningHours(), localNow)) {
            throw new ApiException(HttpStatus.CONFLICT, restaurant.getName() + " is closed right now");
        }
        PaymentMethod paymentMethod = request.paymentMethod() == null ? PaymentMethod.ONLINE : request.paymentMethod();
        if (paymentMethod == PaymentMethod.PAY_LATER && type != OrderType.DINE_IN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAY_LATER is only available for DINE_IN orders");
        }

        Order order = new Order(UUID.randomUUID(), restaurant.getId(), customerId, type, paymentMethod,
                blankToNull(request.notes()), now);
        applyFulfilment(order, restaurant, request);
        long subtotal = addItems(order, restaurant.getId(), request);
        order.setTotals(subtotal, orderProperties.taxOn(subtotal));

        // Payment starts separately (POST .../payment), so no gateway call happens in this transaction.
        // Last step before insert: the counter row stays locked until this transaction commits.
        order.assignDisplayNumber(localNow.toLocalDate(), displayNumbers.next(restaurant.getId(), localNow.toLocalDate()));
        orders.saveAndFlush(order);
        events.changed(order); // a PAY_LATER order goes straight to the kitchen
        return mapper.toResponse(order);
    }

    /** Not transactional: the gateway is called between short transactions inside. */
    public PaymentCheckoutResponse startPayment(UUID customerId, UUID orderId) {
        return payments.startCheckout(orderId, customerId);
    }

    /** Once PAID, the kitchen sees the order as new (pushed by OrderEvents). */
    public OrderResponse confirmPayment(UUID customerId, UUID orderId, ConfirmPaymentRequest request) {
        return payments.confirmCheckout(orderId, customerId, request.providerOrderId(), request.providerPaymentId(), request.signature());
    }

    /** Allowed while PLACED or ACCEPTED (state machine); a captured payment is refunded. */
    @Transactional
    public OrderResponse cancel(UUID customerId, UUID orderId, String reason) {
        Order order = requireOwn(customerId, orderId);
        OrderStateMachine.transition(order, OrderStatus.CANCELLED, OrderActor.CUSTOMER, clock.instant());
        order.setStatusReason(reason == null || reason.isBlank() ? "Cancelled by customer" : reason.strip());
        payments.requestRefundIfPaid(order);
        events.changed(order);
        return mapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(UUID customerId, int page, int size) {
        Page<Order> result = orders.findByCustomerIdOrderByPlacedAtDesc(customerId, PageRequest.of(page, size));
        return PageResponse.of(mapper.toResponses(result.getContent()), page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID customerId, UUID orderId) {
        return mapper.toResponse(requireOwn(customerId, orderId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void applyFulfilment(Order order, Restaurant restaurant, PlaceOrderRequest request) {
        boolean hasTable = request.tableSessionToken() != null && !request.tableSessionToken().isBlank();
        DeliveryAddressRequest address = request.deliveryAddress();
        switch (request.orderType()) {
            case DINE_IN -> {
                if (!hasTable) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "tableSessionToken is required for DINE_IN orders; scan the table's QR code");
                }
                rejectIf(address != null, "deliveryAddress is only allowed for DELIVERY orders");
                // The table comes only from the scanned session, never from the client.
                DiningTable table = tableSessions.requireCurrentTable(request.tableSessionToken(), restaurant.getId());
                order.setDineInTable(table.getId(), table.getLabel());
            }
            case DELIVERY -> {
                if (address == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "deliveryAddress is required for DELIVERY orders");
                }
                rejectIf(hasTable, "tableSessionToken is only allowed for DINE_IN orders");
                double distance = Geo.distanceKm(restaurant.getLatitude(), restaurant.getLongitude(),
                        address.latitude(), address.longitude());
                if (distance > restaurant.getDeliveryRadiusKm()) {
                    throw new ApiException(HttpStatus.CONFLICT, String.format(Locale.ROOT,
                            "Delivery address is %.1f km away; %s delivers within %.1f km",
                            distance, restaurant.getName(), restaurant.getDeliveryRadiusKm()));
                }
                order.setDelivery(address.address().strip(), address.latitude(), address.longitude(),
                        Math.round(distance * 100) / 100.0);
            }
            case TAKEAWAY -> {
                rejectIf(hasTable, "tableSessionToken is only allowed for DINE_IN orders");
                rejectIf(address != null, "deliveryAddress is only allowed for DELIVERY orders");
            }
        }
    }

    /** Snapshots name/price/veg from the menu; every item must belong to the restaurant and be available. */
    private long addItems(Order order, UUID restaurantId, PlaceOrderRequest request) {
        if (request.items().size() > orderProperties.maxLines()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "An order can have at most " + orderProperties.maxLines() + " lines");
        }
        Map<UUID, MenuItem> menu = menuItems.findAllById(request.items().stream().map(OrderLineRequest::menuItemId).toList())
                .stream()
                .filter(m -> m.getRestaurantId().equals(restaurantId))
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));
        long subtotal = 0;
        for (OrderLineRequest line : request.items()) {
            MenuItem item = menu.get(line.menuItemId());
            if (item == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Menu item " + line.menuItemId() + " is not on this restaurant's menu");
            }
            if (!item.isAvailable()) {
                throw new ApiException(HttpStatus.CONFLICT, item.getName() + " is currently unavailable");
            }
            if (line.quantity() > orderProperties.maxQuantityPerLine()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Quantity per line is limited to " + orderProperties.maxQuantityPerLine());
            }
            order.addItem(item.getId(), item.getName(), item.getPricePaise(), item.isVeg(), line.quantity(),
                    blankToNull(line.notes()));
            subtotal = Math.addExact(subtotal, Math.multiplyExact(item.getPricePaise(), (long) line.quantity()));
        }
        return subtotal;
    }

    private Order requireOwn(UUID customerId, UUID orderId) {
        return orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private static void rejectIf(boolean condition, String message) {
        if (condition) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
