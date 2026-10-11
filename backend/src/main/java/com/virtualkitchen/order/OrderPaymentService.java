package com.virtualkitchen.order;

import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserAccountService;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.order.dto.OrderResponse;
import com.virtualkitchen.order.dto.PaymentCheckoutResponse;
import com.virtualkitchen.payment.CounterMethod;
import com.virtualkitchen.payment.Payment;
import com.virtualkitchen.payment.PaymentAttemptStatus;
import com.virtualkitchen.payment.PaymentProperties;
import com.virtualkitchen.payment.PaymentProvider;
import com.virtualkitchen.payment.PaymentProviderException;
import com.virtualkitchen.payment.PaymentRepository;
import com.virtualkitchen.restaurant.RestaurantAccess;
import com.virtualkitchen.restaurant.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Money for orders: checkout, confirmation, webhooks, refunds and counter settlements.
 *
 * <ul>
 *   <li>Amounts are always recomputed from order_items (plus the tax fixed at placement); nothing
 *       the client sends is used as an amount, and captures for any other amount are refused.</li>
 *   <li>Gateway calls never happen while a database transaction or lock is held.</li>
 *   <li>Every state change is idempotent (row lock + status check), so the browser confirmation
 *       and the gateway's webhooks can arrive in any order, any number of times.</li>
 *   <li>Refunds are decided inside the reject/cancel transaction and sent to the gateway after it
 *       commits; failures are retried by a job that first checks for an existing refund.</li>
 * </ul>
 */
@Service
public class OrderPaymentService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentService.class);
    private static final Set<OrderStatus> STOPPED = EnumSet.of(OrderStatus.REJECTED, OrderStatus.CANCELLED);
    private static final Duration REFUND_RETRY_AFTER = Duration.ofMinutes(1);

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final PaymentProperties properties;
    private final RestaurantRepository restaurants;
    private final RestaurantAccess restaurantAccess;
    private final UserAccountService users;
    private final OrderEvents events;
    private final OrderMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OrderPaymentService(OrderRepository orders, PaymentRepository payments, PaymentProvider provider,
                               PaymentProperties properties, RestaurantRepository restaurants,
                               RestaurantAccess restaurantAccess, UserAccountService users, OrderEvents events,
                               OrderMapper mapper, ApplicationEventPublisher publisher,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.orders = orders;
        this.payments = payments;
        this.provider = provider;
        this.properties = properties;
        this.restaurants = restaurants;
        this.restaurantAccess = restaurantAccess;
        this.users = users;
        this.events = events;
        this.mapper = mapper;
        this.publisher = publisher;
        // REQUIRES_NEW: these short transactions also run from AFTER_COMMIT listeners, where a
        // REQUIRED template would join the finished transaction and do nothing transactional.
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Published inside a transaction; the refund call runs after it commits. */
    public record RefundRequested(UUID paymentId) {
    }

    // ── checkout ─────────────────────────────────────────────────────────────

    /**
     * Opens (or reopens) payment for an order: reuses an unpaid checkout for the same amount,
     * otherwise creates a new gateway order. Prepaid orders can be paid while PLACED;
     * pay-at-table orders any time until they're settled.
     */
    public PaymentCheckoutResponse startCheckout(UUID orderId, UUID customerId) {
        Prepared prepared = tx.execute(status -> {
            Order order = requireOwn(orderId, customerId);
            requirePayable(order);
            long amount = amountDue(order);
            Optional<Payment> reusable = payments.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                    .filter(p -> p.getStatus() == PaymentAttemptStatus.CREATED || p.getStatus() == PaymentAttemptStatus.FAILED)
                    .filter(p -> p.getProvider().equals(provider.name()) && p.getAmountPaise() == amount)
                    .findFirst();
            return new Prepared(order.getId(), order.getCustomerId(), order.getRestaurantId(), order.getDisplayNumber(), amount,
                    reusable.map(Payment::getProviderOrderId).orElse(null));
        });
        String providerOrderId = prepared.reusableProviderOrderId();
        if (providerOrderId == null) {
            // Network call: outside any transaction.
            providerOrderId = provider.createOrder(orderId, prepared.amountPaise(), "o_" + orderId).providerOrderId();
            String created = providerOrderId;
            tx.executeWithoutResult(status -> {
                Order order = orders.findById(orderId).orElseThrow();
                requirePayable(order);
                payments.save(Payment.online(orderId, provider.name(), created, prepared.amountPaise(), clock.instant()));
            });
        }
        return checkoutResponse(prepared, providerOrderId);
    }

    /** The browser's report after checkout. Verified with the gateway; never trusted on its own. */
    public OrderResponse confirmCheckout(UUID orderId, UUID customerId, String providerOrderId, String providerPaymentId, String signature) {
        Payment payment = tx.execute(status -> {
            requireOwn(orderId, customerId);
            return payments.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                    .filter(p -> provider.name().equals(p.getProvider()) && providerOrderId.equals(p.getProviderOrderId()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such payment for this order"));
        });
        if (!payment.isSettled()) {
            PaymentProvider.CheckoutResult result = provider.verifyCheckout(providerOrderId, providerPaymentId,
                    signature == null ? "" : signature, payment.getAmountPaise());
            // tx.execute, not a direct call: self-invocation would bypass @Transactional (and the row lock needs one).
            switch (result.outcome()) {
                case INVALID -> throw new ApiException(HttpStatus.BAD_REQUEST, result.reason());
                case CAPTURED -> tx.execute(status -> applyCaptured(provider.name(), providerOrderId, result.providerPaymentId(),
                        result.capturedAmountPaise(), null));
                case FAILED -> tx.execute(status -> applyFailed(provider.name(), providerOrderId, result.providerPaymentId(), result.reason(), null));
                case PENDING -> log.info("Payment {} for order {} not settled yet; waiting for webhook", providerPaymentId, orderId);
            }
        }
        return tx.execute(status -> mapper.toResponse(requireOwn(orderId, customerId)));
    }

    // ── settling (shared by checkout confirmation and webhooks) ──────────────

    /**
     * Money was captured. Idempotent. If the order can no longer use it (rejected, cancelled, or
     * already paid by another attempt), the payment is refunded automatically.
     *
     * @return false if the payment is unknown (not one of ours)
     */
    @Transactional
    public boolean applyCaptured(String providerName, String providerOrderId, String providerPaymentId, long capturedAmountPaise, String rawPayload) {
        Instant now = clock.instant();
        Optional<Payment> found = payments.lockByProviderOrderId(providerName, providerOrderId);
        if (found.isEmpty()) {
            log.warn("Capture for unknown {} order {}", providerName, providerOrderId);
            return false;
        }
        Payment payment = found.get();
        if (rawPayload != null) {
            payment.recordWebhook(rawPayload, now);
        }
        if (payment.isSettled()) {
            return true; // already applied
        }
        if (capturedAmountPaise != payment.getAmountPaise()) {
            log.error("AMOUNT MISMATCH on {} payment {}: captured {} paise, expected {}. Not marking the order paid; needs manual review.",
                    providerName, providerPaymentId, capturedAmountPaise, payment.getAmountPaise());
            payment.markFailed(providerPaymentId, "Captured amount " + capturedAmountPaise + " does not match " + payment.getAmountPaise(), now);
            return true;
        }
        payment.markCaptured(providerPaymentId, now);
        Order order = orders.findById(payment.getOrderId()).orElseThrow();
        if (STOPPED.contains(order.getStatus()) || order.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Payment {} arrived for order {} that is {} / {}; refunding it", providerPaymentId, order.getId(),
                    order.getStatus(), order.getPaymentStatus());
            requestRefund(payment, now);
            if (STOPPED.contains(order.getStatus())) {
                order.markRefundPending(now);
            }
        } else {
            order.markPaid(now);
        }
        events.changed(order);
        return true;
    }

    /** A payment attempt failed. Idempotent; ignored once the payment has settled. */
    @Transactional
    public boolean applyFailed(String providerName, String providerOrderId, String providerPaymentId, String reason, String rawPayload) {
        Instant now = clock.instant();
        Optional<Payment> found = payments.lockByProviderOrderId(providerName, providerOrderId);
        if (found.isEmpty()) {
            log.warn("Failure for unknown {} order {}", providerName, providerOrderId);
            return false;
        }
        Payment payment = found.get();
        if (rawPayload != null) {
            payment.recordWebhook(rawPayload, now);
        }
        if (payment.isSettled()) {
            return true;
        }
        payment.markFailed(providerPaymentId, reason, now);
        Order order = orders.findById(payment.getOrderId()).orElseThrow();
        order.markPaymentFailed(now);
        events.changed(order);
        return true;
    }

    /** The gateway finished a refund. Idempotent. */
    @Transactional
    public boolean applyRefundProcessed(String providerName, String providerPaymentId, String refundId, String rawPayload) {
        Instant now = clock.instant();
        List<Payment> matching = payments.lockByProviderPaymentId(providerName, providerPaymentId);
        if (matching.isEmpty()) {
            log.warn("Refund for unknown {} payment {}", providerName, providerPaymentId);
            return false;
        }
        for (Payment payment : matching) {
            if (rawPayload != null) {
                payment.recordWebhook(rawPayload, now);
            }
            if (payment.getStatus() == PaymentAttemptStatus.REFUND_PENDING || payment.getStatus() == PaymentAttemptStatus.REFUND_REQUESTED) {
                payment.markRefunded(refundId, now);
                settleOrderAfterRefund(payment.getOrderId(), now);
            }
        }
        return true;
    }

    // ── refunds ──────────────────────────────────────────────────────────────

    /**
     * Called inside the transaction that rejects or cancels an order. Gateway payments are
     * refunded after commit; counter payments (cash/UPI to staff) must be returned in person.
     */
    void requestRefundIfPaid(Order order) {
        Instant now = clock.instant();
        boolean refunding = false;
        for (Payment payment : payments.findByOrderIdOrderByCreatedAtDesc(order.getId())) {
            if (payment.getStatus() != PaymentAttemptStatus.CAPTURED) {
                continue;
            }
            if (payment.isCounter()) {
                log.info("Order {} was settled at the counter ({}); refund it in person", order.getId(), payment.getCounterMethod());
                continue;
            }
            requestRefund(payment, now);
            refunding = true;
        }
        if (refunding) {
            order.markRefundPending(now);
        }
    }

    private void requestRefund(Payment payment, Instant now) {
        payment.requestRefund(now);
        publisher.publishEvent(new RefundRequested(payment.getId()));
    }

    /** After the order-change broadcast (see OrderEventBroadcaster), so pushes stay in order. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @org.springframework.core.annotation.Order(Ordered.LOWEST_PRECEDENCE) // qualified: clashes with our Order entity
    public void onRefundRequested(RefundRequested event) {
        processRefund(event.paymentId());
    }

    /** Retries refunds the gateway didn't accept (outage, timeout). */
    @Scheduled(fixedDelayString = "PT2M", initialDelayString = "PT1M")
    public void retryPendingRefunds() {
        Instant cutoff = clock.instant().minus(REFUND_RETRY_AFTER);
        payments.findByStatusAndRefundRequestedAtBefore(PaymentAttemptStatus.REFUND_REQUESTED, cutoff)
                .forEach(p -> processRefund(p.getId()));
    }

    void processRefund(UUID paymentId) {
        Payment payment = tx.execute(status -> payments.findById(paymentId)
                .filter(p -> p.getStatus() == PaymentAttemptStatus.REFUND_REQUESTED)
                .orElse(null));
        if (payment == null) {
            return;
        }
        PaymentProvider.Refund refund;
        try {
            // A previous attempt may have reached the gateway even if we never heard back.
            refund = provider.findRefund(payment.getProviderPaymentId())
                    .orElseGet(() -> provider.refund(payment.getProviderPaymentId(), payment.getAmountPaise(), "r_" + payment.getId()));
        } catch (PaymentProviderException | IllegalStateException e) {
            log.warn("Refund of payment {} not accepted yet ({}); will retry", paymentId, e.getMessage());
            return;
        }
        tx.executeWithoutResult(status -> {
            Payment locked = payments.lockById(paymentId).orElseThrow();
            if (locked.getStatus() != PaymentAttemptStatus.REFUND_REQUESTED) {
                return;
            }
            Instant now = clock.instant();
            if (refund.processed()) {
                locked.markRefunded(refund.refundId(), now);
                settleOrderAfterRefund(locked.getOrderId(), now);
            } else {
                locked.refundAccepted(refund.refundId(), now);
            }
        });
    }

    /** A stopped order shows REFUNDED once none of its payments still hold money. */
    private void settleOrderAfterRefund(UUID orderId, Instant now) {
        Order order = orders.findById(orderId).orElseThrow();
        boolean stillHeld = payments.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .anyMatch(p -> !p.isCounter() && EnumSet.of(PaymentAttemptStatus.CAPTURED, PaymentAttemptStatus.REFUND_REQUESTED,
                        PaymentAttemptStatus.REFUND_PENDING).contains(p.getStatus()));
        if (STOPPED.contains(order.getStatus()) && !stillHeld) {
            order.markRefunded(now);
        }
        events.changed(order);
    }

    // ── counter (owner) ──────────────────────────────────────────────────────

    /** Pay-at-table bills still to be settled (including orders already served). */
    @Transactional(readOnly = true)
    public List<OrderResponse> unpaidBills(UUID ownerId, UUID restaurantId) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        return mapper.toResponses(orders.findByRestaurantIdAndPaymentStatusAndStatusNotInOrderByPlacedAtAsc(
                restaurantId, PaymentStatus.PAY_LATER, STOPPED));
    }

    /** Owner took the money in person. The amount is the order's own, never an input. */
    @Transactional
    public OrderResponse markPaidAtCounter(UUID ownerId, UUID restaurantId, UUID orderId, CounterMethod method) {
        restaurantAccess.requireOwned(restaurantId, ownerId);
        Order order = orders.findByIdAndRestaurantIdAndPaymentStatusIn(orderId, restaurantId, PaymentStatus.VISIBLE_TO_RESTAURANT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getPaymentStatus() != PaymentStatus.PAY_LATER || STOPPED.contains(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only open pay-at-table bills can be settled at the counter");
        }
        Instant now = clock.instant();
        payments.save(Payment.atCounter(order.getId(), amountDue(order), method, ownerId, now));
        order.markPaid(now);
        orders.saveAndFlush(order); // @Version: loses cleanly against a simultaneous online payment
        events.changed(order);
        return mapper.toResponse(order);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Recomputed from the stored order lines; must agree with the totals fixed at placement. */
    static long amountDue(Order order) {
        long subtotal = order.getItems().stream().mapToLong(OrderItem::getLineTotalPaise).sum();
        if (subtotal != order.getSubtotalPaise() || subtotal + order.getTaxPaise() != order.getTotalPaise()) {
            throw new IllegalStateException("Order " + order.getId() + " totals don't match its lines");
        }
        return Math.addExact(subtotal, order.getTaxPaise());
    }

    private static void requirePayable(Order order) {
        if (STOPPED.contains(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Order is " + order.getStatus() + " and can't be paid");
        }
        boolean payable = order.getPaymentMethod() == PaymentMethod.ONLINE
                ? order.getStatus() == OrderStatus.PLACED
                        && (order.getPaymentStatus() == PaymentStatus.PENDING || order.getPaymentStatus() == PaymentStatus.FAILED)
                : order.getPaymentStatus() == PaymentStatus.PAY_LATER;
        if (!payable) {
            throw new ApiException(HttpStatus.CONFLICT, "Order has no outstanding payment (" + order.getPaymentStatus() + ")");
        }
    }

    private Order requireOwn(UUID orderId, UUID customerId) {
        return orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private PaymentCheckoutResponse checkoutResponse(Prepared prepared, String providerOrderId) {
        String restaurantName = restaurants.findById(prepared.restaurantId()).map(r -> r.getName()).orElse(properties.merchantName());
        User customer = users.getById(prepared.customerId());
        return new PaymentCheckoutResponse(provider.name(), providerOrderId, provider.publicKeyId(), prepared.amountPaise(), "INR",
                properties.merchantName(), "Order #" + prepared.displayNumber() + " at " + restaurantName,
                customer.getName(), customer.getPhone());
    }

    private record Prepared(UUID orderId, UUID customerId, UUID restaurantId, int displayNumber, long amountPaise,
                            String reusableProviderOrderId) {
    }
}
