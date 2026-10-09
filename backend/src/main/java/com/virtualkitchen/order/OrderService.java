package com.virtualkitchen.order;

import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.geo.Geo;
import com.virtualkitchen.common.web.PageResponse;
import com.virtualkitchen.menu.MenuItem;
import com.virtualkitchen.menu.MenuItemRepository;
import com.virtualkitchen.order.dto.DeliveryAddressRequest;
import com.virtualkitchen.order.dto.OrderLineRequest;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.PlaceOrderRequest;
import com.virtualkitchen.payment.PaymentProvider;
import com.virtualkitchen.restaurant.OpeningSlot;
import com.virtualkitchen.restaurant.OrderType;
import com.virtualkitchen.restaurant.Restaurant;
import com.virtualkitchen.restaurant.RestaurantProperties;
import com.virtualkitchen.restaurant.RestaurantRepository;
import com.virtualkitchen.restaurant.RestaurantStatus;
import com.virtualkitchen.restaurant.table.DiningTable;
import com.virtualkitchen.restaurant.table.DiningTableRepository;
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
    private final DiningTableRepository tables;
    private final DisplayNumberAllocator displayNumbers;
    private final OrderPayments payments;
    private final OrderMapper mapper;
    private final OrderProperties orderProperties;
    private final RestaurantProperties restaurantProperties;
    private final Clock clock;

    public OrderService(OrderRepository orders, RestaurantRepository restaurants, MenuItemRepository menuItems,
                        DiningTableRepository tables, DisplayNumberAllocator displayNumbers, OrderPayments payments,
                        OrderMapper mapper, OrderProperties orderProperties, RestaurantProperties restaurantProperties,
                        Clock clock) {
        this.orders = orders;
        this.restaurants = restaurants;
        this.menuItems = menuItems;
        this.tables = tables;
        this.displayNumbers = displayNumbers;
        this.payments = payments;
        this.mapper = mapper;
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

        // Gateway call before taking the display-number lock, so the lock isn't held during it.
        PaymentProvider.PaymentSession session = paymentMethod == PaymentMethod.ONLINE ? payments.start(order) : null;

        // Last step before insert: the counter row stays locked until this transaction commits.
        order.assignDisplayNumber(localNow.toLocalDate(), displayNumbers.next(restaurant.getId(), localNow.toLocalDate()));
        orders.saveAndFlush(order);
        return session == null ? mapper.toResponse(order) : mapper.toResponse(order, session);
    }

    @Transactional
    public OrderResponse confirmPayment(UUID customerId, UUID orderId, String providerPaymentId, String signature) {
        Order order = requireOwn(customerId, orderId);
        if (order.getPaymentMethod() != PaymentMethod.ONLINE
                || (order.getPaymentStatus() != PaymentStatus.PENDING && order.getPaymentStatus() != PaymentStatus.FAILED)) {
            throw new ApiException(HttpStatus.CONFLICT, "Order has no outstanding payment (status " + order.getPaymentStatus() + ")");
        }
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ApiException(HttpStatus.CONFLICT, "Order is " + order.getStatus() + " and can no longer be paid");
        }
        payments.confirm(order, providerPaymentId, signature);
        return mapper.toResponse(order);
    }

    /** Allowed while PLACED or ACCEPTED (state machine); a captured payment is refunded. */
    @Transactional
    public OrderResponse cancel(UUID customerId, UUID orderId, String reason) {
        Order order = requireOwn(customerId, orderId);
        OrderStateMachine.transition(order, OrderStatus.CANCELLED, OrderActor.CUSTOMER, clock.instant());
        order.setStatusReason(reason == null || reason.isBlank() ? "Cancelled by customer" : reason.strip());
        payments.refundIfPaid(order);
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
        boolean hasTable = request.tableQrToken() != null && !request.tableQrToken().isBlank();
        DeliveryAddressRequest address = request.deliveryAddress();
        switch (request.orderType()) {
            case DINE_IN -> {
                if (!hasTable) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "tableQrToken is required for DINE_IN orders");
                }
                rejectIf(address != null, "deliveryAddress is only allowed for DELIVERY orders");
                DiningTable table = tables.findByQrToken(request.tableQrToken())
                        .filter(t -> t.getRestaurantId().equals(restaurant.getId()))
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid table code for this restaurant"));
                order.setDineInTable(table.getId(), table.getLabel());
            }
            case DELIVERY -> {
                if (address == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "deliveryAddress is required for DELIVERY orders");
                }
                rejectIf(hasTable, "tableQrToken is only allowed for DINE_IN orders");
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
                rejectIf(hasTable, "tableQrToken is only allowed for DINE_IN orders");
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
