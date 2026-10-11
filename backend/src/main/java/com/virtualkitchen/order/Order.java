package com.virtualkitchen.order;

import com.virtualkitchen.restaurant.OrderType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, updatable = false, length = 16)
    private OrderType orderType;

    /** Restaurant-local (Asia/Kolkata) date the display number belongs to. */
    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "display_number", nullable = false, updatable = false)
    private int displayNumber;

    @Column(name = "table_id")
    private UUID tableId;

    @Column(name = "table_label", length = 20, updatable = false)
    private String tableLabel;

    @Column(name = "delivery_address", length = 500, updatable = false)
    private String deliveryAddress;

    @Column(name = "delivery_latitude", updatable = false)
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude", updatable = false)
    private Double deliveryLongitude;

    @Column(name = "delivery_distance_km", updatable = false)
    private Double deliveryDistanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 16)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private PaymentStatus paymentStatus;

    // Gateway details (ids, attempts, raw webhooks) live in the payments table; see OrderPaymentService.

    @Column(name = "subtotal_paise", nullable = false, updatable = false)
    private long subtotalPaise;

    @Column(name = "tax_paise", nullable = false, updatable = false)
    private long taxPaise;

    @Column(name = "total_paise", nullable = false, updatable = false)
    private long totalPaise;

    @Column(length = 500, updatable = false)
    private String notes;

    /** Why the order was rejected or cancelled. */
    @Column(name = "status_reason", length = 300)
    private String statusReason;

    @Column(name = "prepared_by_chef_id")
    private UUID preparedByChefId;

    @Column(name = "station_id")
    private UUID stationId;

    @Column(name = "placed_at", nullable = false, updatable = false) private Instant placedAt;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "rejected_at") private Instant rejectedAt;
    @Column(name = "preparing_at") private Instant preparingAt;
    @Column(name = "ready_at") private Instant readyAt;
    @Column(name = "served_at") private Instant servedAt;
    @Column(name = "picked_up_at") private Instant pickedUpAt;
    @Column(name = "out_for_delivery_at") private Instant outForDeliveryAt;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "refunded_at") private Instant refundedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    /** Optimistic locking: two staff changing the same order concurrently → one gets 409. */
    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // for JPA
    }

    /** Built by OrderService; the display number is assigned last, just before insert. */
    Order(UUID id, UUID restaurantId, UUID customerId, OrderType orderType, PaymentMethod paymentMethod,
          String notes, Instant now) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.customerId = customerId;
        this.orderType = orderType;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentMethod == PaymentMethod.PAY_LATER ? PaymentStatus.PAY_LATER : PaymentStatus.PENDING;
        this.status = OrderStatus.PLACED;
        this.notes = notes;
        this.placedAt = now;
        this.updatedAt = now;
    }

    void addItem(UUID menuItemId, String name, long unitPricePaise, boolean veg, int quantity, String notes) {
        items.add(new OrderItem(this, items.size() + 1, menuItemId, name, unitPricePaise, veg, quantity, notes));
    }

    void setDineInTable(UUID tableId, String tableLabel) {
        this.tableId = tableId;
        this.tableLabel = tableLabel;
    }

    void setDelivery(String address, double latitude, double longitude, double distanceKm) {
        this.deliveryAddress = address;
        this.deliveryLatitude = latitude;
        this.deliveryLongitude = longitude;
        this.deliveryDistanceKm = distanceKm;
    }

    void setTotals(long subtotalPaise, long taxPaise) {
        this.subtotalPaise = subtotalPaise;
        this.taxPaise = taxPaise;
        this.totalPaise = Math.addExact(subtotalPaise, taxPaise);
    }

    void assignDisplayNumber(LocalDate businessDate, int displayNumber) {
        this.businessDate = businessDate;
        this.displayNumber = displayNumber;
    }

    // ── payment ──────────────────────────────────────────────────────────────

    // Only OrderPaymentService calls these; it keeps them consistent with the payments table.

    void markPaid(Instant now) {
        this.paymentStatus = PaymentStatus.PAID;
        this.paidAt = now;
        this.updatedAt = now;
    }

    /**
     * A failed attempt only matters for prepaid orders still waiting for payment. A pay-at-table
     * order that tried to pay online and failed is still a valid pay-at-table order.
     */
    void markPaymentFailed(Instant now) {
        if (paymentMethod == PaymentMethod.ONLINE && paymentStatus == PaymentStatus.PENDING) {
            this.paymentStatus = PaymentStatus.FAILED;
            this.updatedAt = now;
        }
    }

    void markRefundPending(Instant now) {
        this.paymentStatus = PaymentStatus.REFUND_PENDING;
        this.updatedAt = now;
    }

    void markRefunded(Instant now) {
        this.paymentStatus = PaymentStatus.REFUNDED;
        this.refundedAt = now;
        this.updatedAt = now;
    }

    public boolean isVisibleToRestaurant() {
        return PaymentStatus.VISIBLE_TO_RESTAURANT.contains(paymentStatus);
    }

    // ── status (only via OrderStateMachine) ──────────────────────────────────

    void assignPreparation(UUID chefId, UUID stationId) {
        this.preparedByChefId = chefId;
        this.stationId = stationId;
    }

    void setStatusReason(String reason) {
        this.statusReason = reason;
    }

    /** Package-private on purpose: {@link OrderStateMachine#transition} is the only caller. */
    void applyStatus(OrderStatus to, Instant now) {
        this.status = to;
        this.updatedAt = now;
        switch (to) {
            case PLACED -> placedAt = now;
            case ACCEPTED -> acceptedAt = now;
            case REJECTED -> rejectedAt = now;
            case PREPARING -> preparingAt = now;
            case READY -> readyAt = now;
            case SERVED -> servedAt = now;
            case PICKED_UP -> pickedUpAt = now;
            case OUT_FOR_DELIVERY -> outForDeliveryAt = now;
            case DELIVERED -> deliveredAt = now;
            case CANCELLED -> cancelledAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getCustomerId() { return customerId; }
    public OrderType getOrderType() { return orderType; }
    public LocalDate getBusinessDate() { return businessDate; }
    public int getDisplayNumber() { return displayNumber; }
    public UUID getTableId() { return tableId; }
    public String getTableLabel() { return tableLabel; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public Double getDeliveryLatitude() { return deliveryLatitude; }
    public Double getDeliveryLongitude() { return deliveryLongitude; }
    public Double getDeliveryDistanceKm() { return deliveryDistanceKm; }
    public OrderStatus getStatus() { return status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public long getSubtotalPaise() { return subtotalPaise; }
    public long getTaxPaise() { return taxPaise; }
    public long getTotalPaise() { return totalPaise; }
    public String getNotes() { return notes; }
    public String getStatusReason() { return statusReason; }
    public UUID getPreparedByChefId() { return preparedByChefId; }
    public UUID getStationId() { return stationId; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public Instant getPreparingAt() { return preparingAt; }
    public Instant getReadyAt() { return readyAt; }
    public Instant getServedAt() { return servedAt; }
    public Instant getPickedUpAt() { return pickedUpAt; }
    public Instant getOutForDeliveryAt() { return outForDeliveryAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getRefundedAt() { return refundedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return items; }
}
