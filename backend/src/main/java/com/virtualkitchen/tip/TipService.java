package com.virtualkitchen.tip;

import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserAccountService;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.storage.ObjectStorage;
import com.virtualkitchen.kitchen.chef.Chef;
import com.virtualkitchen.kitchen.chef.ChefRepository;
import com.virtualkitchen.order.Order;
import com.virtualkitchen.order.OrderRepository;
import com.virtualkitchen.order.OrderStatus;
import com.virtualkitchen.order.dto.ConfirmPaymentRequest;
import com.virtualkitchen.order.dto.PaymentCheckoutResponse;
import com.virtualkitchen.payment.PaymentProperties;
import com.virtualkitchen.payment.PaymentProvider;
import com.virtualkitchen.payment.PaymentProvider.CheckoutResult;
import com.virtualkitchen.payment.PayoutProvider;
import com.virtualkitchen.tip.dto.ChefTipResponse;
import com.virtualkitchen.tip.dto.ChefTipsPage;
import com.virtualkitchen.tip.dto.OrderTipsResponse;
import com.virtualkitchen.tip.dto.TipCheckoutResponse;
import com.virtualkitchen.tip.dto.TipRequest;
import com.virtualkitchen.tip.dto.TipResponse;
import com.virtualkitchen.tip.dto.TippableChef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tips go straight to the chef: each tip is its own gateway order with a Route transfer of the chef's share
 * (100% unless {@code app.tips.platform-fee-bps} says otherwise) to the chef's linked account. The platform never
 * holds the chef's share; no one (owner or platform) can redirect it. Gateway calls happen outside transactions;
 * state changes are idempotent (checkout confirmation and webhooks may race).
 */
@Service
public class TipService {

    private static final Logger log = LoggerFactory.getLogger(TipService.class);
    private static final Set<OrderStatus> NOT_TIPPABLE = EnumSet.of(OrderStatus.REJECTED, OrderStatus.CANCELLED);
    private static final Set<TipStatus> RECEIVED = EnumSet.of(TipStatus.PAID, TipStatus.TRANSFERRED);

    private final TipRepository tips;
    private final ChefPayoutAccountRepository payoutAccounts;
    private final OrderRepository orders;
    private final ChefRepository chefs;
    private final ChefFollowService follows;
    private final UserAccountService users;
    private final PaymentProvider payments;
    private final PayoutProvider payouts;
    private final PaymentProperties paymentProperties;
    private final TipProperties properties;
    private final ObjectStorage storage;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TipService(TipRepository tips, ChefPayoutAccountRepository payoutAccounts, OrderRepository orders, ChefRepository chefs,
                      ChefFollowService follows, UserAccountService users, PaymentProvider payments, PayoutProvider payouts,
                      PaymentProperties paymentProperties, TipProperties properties, ObjectStorage storage, JdbcTemplate jdbc,
                      PlatformTransactionManager transactions, Clock clock) {
        this.tips = tips;
        this.payoutAccounts = payoutAccounts;
        this.orders = orders;
        this.chefs = chefs;
        this.follows = follows;
        this.users = users;
        this.payments = payments;
        this.payouts = payouts;
        this.paymentProperties = paymentProperties;
        this.properties = properties;
        this.storage = storage;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    // ── customer ─────────────────────────────────────────────────────────────

    /** Who can be tipped on this order and what this customer already tipped. */
    public OrderTipsResponse options(UUID customerId, UUID orderId) {
        return tx.execute(s -> {
            Order order = customerOrder(customerId, orderId);
            Map<UUID, TippableChef> chefsById = new LinkedHashMap<>();
            if (!NOT_TIPPABLE.contains(order.getStatus())) {
                if (order.getPreparedByChefId() != null) {
                    chefs.findById(order.getPreparedByChefId()).filter(this::tippable)
                            .ifPresent(c -> chefsById.put(c.getId(), toTippable(c, true)));
                }
                follows.followed(customerId, order.getRestaurantId()).stream()
                        .filter(f -> f.tippable() && !chefsById.containsKey(f.chefId()))
                        .forEach(f -> chefsById.put(f.chefId(), new TippableChef(f.chefId(), f.displayName(), f.photoUrl(), false)));
            }
            List<TipResponse> mine = tips.findByOrderIdAndCustomerIdOrderByCreatedAtDesc(orderId, customerId).stream()
                    .filter(t -> t.getStatus() != TipStatus.CREATED)
                    .map(this::toResponse).toList();
            return new OrderTipsResponse(new ArrayList<>(chefsById.values()), mine, properties.presetsPaise(),
                    properties.minPaise(), properties.maxPaise());
        });
    }

    public TipCheckoutResponse start(UUID customerId, UUID orderId, TipRequest request) {
        long amount = request.amountPaise();
        if (amount < properties.minPaise() || amount > properties.maxPaise()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tips must be between ₹" + properties.minPaise() / 100 + " and ₹"
                    + properties.maxPaise() / 100);
        }
        long fee = properties.feeOn(amount);
        Tip tip = tx.execute(s -> {
            Order order = customerOrder(customerId, orderId);
            if (NOT_TIPPABLE.contains(order.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "This order was " + order.getStatus().name().toLowerCase());
            }
            Chef chef = chefs.findById(request.chefId())
                    .filter(c -> c.getRemovedAt() == null && c.getRestaurantId().equals(order.getRestaurantId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef not found at this restaurant"));
            boolean cooked = chef.getId().equals(order.getPreparedByChefId());
            if (!cooked && !follows.follows(customerId, chef.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You can tip the chef who cooked this order, or chefs you follow");
            }
            ChefPayoutAccount account = payoutAccounts.findById(chef.getId()).filter(a -> a.getStatus() == PayoutStatus.ACTIVE)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, chef.getDisplayName() + " can't receive tips yet"));
            if (!account.getProvider().equals(payouts.name())) {
                throw new ApiException(HttpStatus.CONFLICT, chef.getDisplayName() + " can't receive tips right now");
            }
            return tips.save(new Tip(orderId, chef.getId(), customerId, request.source(), amount, fee, payouts.name(),
                    account.getProviderAccountId(), clock.instant()));
        });

        String chefName = chefs.findById(tip.getChefId()).map(Chef::getDisplayName).orElse("the chef");
        String description = "Tip for " + chefName;
        PaymentProvider.ProviderOrder providerOrder;
        try {
            providerOrder = payouts.createTransferOrder(tip.getId(), amount, tip.getLinkedAccountId(), tip.getChefAmountPaise(),
                    "tip_" + tip.getId().toString().substring(0, 8), description);
        } catch (RuntimeException e) {
            tx.executeWithoutResult(s -> tips.lockById(tip.getId()).ifPresent(t -> t.markFailed(null, "Could not start the payment", clock.instant())));
            throw e;
        }
        tx.executeWithoutResult(s -> tips.lockById(tip.getId()).ifPresent(t -> t.attachProviderOrder(providerOrder.providerOrderId(), clock.instant())));

        User customer = users.getById(customerId);
        PaymentCheckoutResponse checkout = new PaymentCheckoutResponse(payments.name(), providerOrder.providerOrderId(), payments.publicKeyId(),
                amount, "INR", paymentProperties.merchantName(), description, customer.getName(), customer.getPhone());
        return new TipCheckoutResponse(toResponse(tip), checkout);
    }

    /** The browser's report after checkout: verified with the gateway before anything changes. */
    public TipResponse confirm(UUID customerId, UUID orderId, UUID tipId, ConfirmPaymentRequest result) {
        Tip tip = tx.execute(s -> tips.findById(tipId)
                .filter(t -> t.getOrderId().equals(orderId) && t.getCustomerId().equals(customerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tip not found")));
        if (tip.isPaid()) {
            return toResponse(tip);
        }
        if (tip.getProviderOrderId() == null || !tip.getProviderOrderId().equals(result.providerOrderId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This payment doesn't belong to this tip");
        }
        CheckoutResult checked = payments.verifyCheckout(result.providerOrderId(), result.providerPaymentId(),
                result.signature() == null ? "" : result.signature(), tip.getAmountPaise());
        switch (checked.outcome()) {
            case CAPTURED -> applyCaptured(payments.name(), tip.getProviderOrderId(), checked.providerPaymentId(), checked.capturedAmountPaise());
            case FAILED -> applyFailed(payments.name(), tip.getProviderOrderId(), checked.providerPaymentId(), checked.reason());
            case INVALID -> throw new ApiException(HttpStatus.BAD_REQUEST, checked.reason());
            case PENDING -> log.info("Tip {} payment {} still pending; waiting for the webhook", tip.getId(), checked.providerPaymentId());
        }
        return tx.execute(s -> toResponse(tips.findById(tipId).orElseThrow()));
    }

    // ── gateway events (checkout confirmation or webhooks; idempotent) ──────

    /** @return false when the gateway order isn't a tip */
    public boolean applyCaptured(String provider, String providerOrderId, String paymentId, long capturedPaise) {
        Boolean known = tx.execute(s -> {
            Optional<Tip> found = tips.lockByProviderOrderId(provider, providerOrderId);
            if (found.isEmpty()) {
                return false;
            }
            Tip tip = found.get();
            if (tip.isPaid()) {
                return true;
            }
            Instant now = clock.instant();
            if (capturedPaise != tip.getAmountPaise()) {
                log.error("AMOUNT MISMATCH on tip {} payment {}: captured {} paise, expected {}. Needs manual review.",
                        tip.getId(), paymentId, capturedPaise, tip.getAmountPaise());
                tip.markFailed(paymentId, "Captured amount " + capturedPaise + " does not match " + tip.getAmountPaise(), now);
                return true;
            }
            tip.markPaid(paymentId, now);
            if (payouts.transfersSettleImmediately()) {
                tip.markTransferred("instant_" + paymentId, now);
            }
            return true;
        });
        return Boolean.TRUE.equals(known);
    }

    public boolean applyFailed(String provider, String providerOrderId, String paymentId, String reason) {
        Boolean known = tx.execute(s -> {
            Optional<Tip> found = tips.lockByProviderOrderId(provider, providerOrderId);
            found.filter(t -> !t.isPaid()).ifPresent(t -> t.markFailed(paymentId, reason == null ? "Payment failed" : reason, clock.instant()));
            return found.isPresent();
        });
        return Boolean.TRUE.equals(known);
    }

    /** Route reports the chef's share has reached their linked account. */
    public boolean applyTransferProcessed(String provider, String paymentId, String transferId) {
        Boolean known = tx.execute(s -> {
            Optional<Tip> found = tips.lockByProviderPaymentId(provider, paymentId);
            found.filter(t -> t.getStatus() == TipStatus.PAID).ifPresent(t -> t.markTransferred(transferId, clock.instant()));
            return found.isPresent();
        });
        return Boolean.TRUE.equals(known);
    }

    public boolean applyTransferFailed(String provider, String paymentId, String reason) {
        Boolean known = tx.execute(s -> {
            Optional<Tip> found = tips.lockByProviderPaymentId(provider, paymentId);
            found.ifPresent(t -> {
                log.error("Route transfer FAILED for tip {} (payment {}): {}. The chef's money is held at the gateway; "
                        + "retry the transfer from the Razorpay Dashboard.", t.getId(), paymentId, reason);
                t.noteTransferProblem(reason, clock.instant());
            });
            return found.isPresent();
        });
        return Boolean.TRUE.equals(known);
    }

    // ── chef ─────────────────────────────────────────────────────────────────

    public ChefTipsPage chefTips(UUID chefUserId, int page, int size) {
        return tx.execute(s -> {
            Chef chef = chefs.findByUserIdAndRemovedAtIsNull(chefUserId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chef profile not found"));
            Page<Tip> result = tips.findByChefIdAndStatusInOrderByCreatedAtDesc(chef.getId(), RECEIVED, PageRequest.of(page, size));
            Map<UUID, Integer> numbers = new LinkedHashMap<>();
            result.getContent().forEach(t -> numbers.computeIfAbsent(t.getOrderId(),
                    id -> orders.findById(id).map(Order::getDisplayNumber).orElse(0)));
            Map<String, Object> totals = jdbc.queryForMap("""
                    SELECT count(*) AS n, coalesce(sum(chef_amount_paise), 0) AS total
                    FROM tips WHERE chef_id = ? AND status IN ('PAID', 'TRANSFERRED')
                    """, chef.getId());
            List<ChefTipResponse> items = result.getContent().stream().map(t -> new ChefTipResponse(t.getId(), numbers.get(t.getOrderId()),
                    t.getAmountPaise(), t.getChefAmountPaise(), t.getStatus(), t.getSource(), t.getCreatedAt(), t.getPaidAt(),
                    t.getTransferredAt())).toList();
            return new ChefTipsPage(items, page, size, result.getTotalElements(), result.getTotalPages(),
                    ((Number) totals.get("n")).longValue(), ((Number) totals.get("total")).longValue());
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Order customerOrder(UUID customerId, UUID orderId) {
        return orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private boolean tippable(Chef chef) {
        return chef.getRemovedAt() == null && payoutAccounts.findById(chef.getId())
                .map(a -> a.getStatus() == PayoutStatus.ACTIVE && a.getProvider().equals(payouts.name())).orElse(false);
    }

    private TippableChef toTippable(Chef chef, boolean cooked) {
        return new TippableChef(chef.getId(), chef.getDisplayName(), storage.downloadUrl(chef.getPhotoKey()), cooked);
    }

    private TipResponse toResponse(Tip tip) {
        String chefName = chefs.findById(tip.getChefId()).map(Chef::getDisplayName).orElse("");
        return new TipResponse(tip.getId(), tip.getChefId(), chefName, tip.getAmountPaise(), tip.getStatus(), tip.getSource(),
                tip.getCreatedAt(), tip.getPaidAt());
    }
}
