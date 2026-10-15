package com.virtualkitchen.billing;

import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserAccountService;
import com.virtualkitchen.billing.dto.BillingOverviewResponse;
import com.virtualkitchen.billing.dto.ConfirmSubscriptionRequest;
import com.virtualkitchen.billing.dto.PlanResponse;
import com.virtualkitchen.billing.dto.SubscriptionCheckoutResponse;
import com.virtualkitchen.common.billing.PlanLimits;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.settings.PlatformSettings;
import com.virtualkitchen.kitchen.KitchenStationRepository;
import com.virtualkitchen.payment.PaymentProperties;
import com.virtualkitchen.payment.SubscriptionProvider;
import com.virtualkitchen.restaurant.Restaurant;
import com.virtualkitchen.restaurant.RestaurantAccess;
import com.virtualkitchen.restaurant.RestaurantCreatedEvent;
import com.virtualkitchen.restaurant.RestaurantRepository;
import com.virtualkitchen.restaurant.RestaurantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Restaurant subscriptions on Razorpay Subscriptions. New restaurants start a free trial; the owner authorises a
 * recurring mandate once; Razorpay charges monthly and reports by webhook. A failed charge (or a trial that ends
 * unpaid) starts a grace period; after it the listing is hidden (status SUSPENDED, reason BILLING) but nothing is
 * deleted, and the next successful charge lists it again. Gateway calls run outside database transactions.
 */
@Service
public class BillingService implements PlanLimits {

    static final String TRIAL_DAYS = "billing.trial_days";
    static final String GRACE_DAYS = "billing.grace_days";
    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final PlanRepository plans;
    private final RestaurantSubscriptionRepository subscriptions;
    private final RestaurantRepository restaurants;
    private final RestaurantAccess access;
    private final RestaurantService restaurantService;
    private final KitchenStationRepository stations;
    private final SubscriptionProvider gateway;
    private final InvoiceService invoices;
    private final UserAccountService users;
    private final PlatformSettings settings;
    private final BillingProperties properties;
    private final PaymentProperties paymentProperties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public BillingService(PlanRepository plans, RestaurantSubscriptionRepository subscriptions, RestaurantRepository restaurants,
                          RestaurantAccess access, RestaurantService restaurantService, KitchenStationRepository stations,
                          SubscriptionProvider gateway, InvoiceService invoices, UserAccountService users, PlatformSettings settings,
                          BillingProperties properties, PaymentProperties paymentProperties, PlatformTransactionManager transactions,
                          Clock clock) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.restaurants = restaurants;
        this.access = access;
        this.restaurantService = restaurantService;
        this.stations = stations;
        this.gateway = gateway;
        this.invoices = invoices;
        this.users = users;
        this.settings = settings;
        this.properties = properties;
        this.paymentProperties = paymentProperties;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    // ── trial ────────────────────────────────────────────────────────────────

    /** Runs inside the transaction that creates the restaurant. */
    @EventListener
    public void restaurantCreated(RestaurantCreatedEvent event) {
        Instant now = clock.instant();
        Plan plan = defaultPlan();
        int trialDays = settings.getInt(TRIAL_DAYS, properties.defaultTrialDays());
        subscriptions.save(new RestaurantSubscription(event.restaurantId(), plan.getId(), now.plus(Duration.ofDays(trialDays)), now));
    }

    private Plan defaultPlan() {
        return plans.findByActiveTrueOrderBySortOrderAscPricePaiseAsc().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No active plan: create one in Admin → Billing"));
    }

    // ── owner ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BillingOverviewResponse overview(UUID ownerId, UUID restaurantId) {
        Restaurant restaurant = access.requireOwned(restaurantId, ownerId);
        RestaurantSubscription s = subscription(restaurantId);
        Plan plan = plans.findById(s.getPlanId()).orElseThrow();
        Plan pending = s.getPendingPlanId() == null ? null : plans.findById(s.getPendingPlanId()).orElse(null);
        Instant nextCharge = !s.isPaymentMethodReady() || s.getCancelledAt() != null ? null
                : s.getStatus() == SubscriptionStatus.TRIALING ? s.getTrialEndsAt() : s.getCurrentPeriodEnd();
        List<PlanResponse> offered = plans.findByActiveTrueOrderBySortOrderAscPricePaiseAsc().stream().map(this::toResponse).toList();
        return new BillingOverviewResponse(s.getStatus(), toResponse(plan), pending == null ? null : toResponse(pending), s.getTrialEndsAt(),
                s.getCurrentPeriodEnd(), nextCharge, s.getGraceUntil(), s.isPaymentMethodReady(), s.getCancelledAt() != null,
                restaurant.getLegalName() != null && restaurant.getBillingStateCode() != null, offered, invoices.list(restaurantId));
    }

    /**
     * Starts (or resumes) the recurring payment set-up on {@code planId}: a gateway subscription whose first charge is
     * at the end of the trial (or now, if the trial is over). The browser then opens the gateway checkout.
     */
    public SubscriptionCheckoutResponse startCheckout(UUID ownerId, UUID restaurantId, UUID planId) {
        Restaurant restaurant = tx.execute(s -> access.requireOwned(restaurantId, ownerId));
        if (restaurant.getLegalName() == null || restaurant.getBillingStateCode() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Add the legal name and GST state (Profile → Licence & tax details) first; they go on your invoices");
        }
        Plan plan = activePlan(planId);
        requireFits(restaurantId, plan);
        RestaurantSubscription current = tx.execute(s -> subscription(restaurantId));
        if (current.isPaymentMethodReady() && current.getCancelledAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Payments are already set up; change plan instead");
        }
        String gatewayPlan = gatewayPlan(plan);
        Instant now = clock.instant();
        Instant startAt = current.getTrialEndsAt().isAfter(now) && current.getStatus() == SubscriptionStatus.TRIALING ? current.getTrialEndsAt() : null;
        SubscriptionProvider.NewSubscription created = gateway.createSubscription(gatewayPlan, startAt,
                Map.of("restaurant_id", restaurantId.toString(), "plan", plan.getCode()));
        tx.executeWithoutResult(s -> {
            RestaurantSubscription sub = subscriptions.lock(restaurantId).orElseThrow();
            sub.changePlan(plan.getId(), now);
            sub.attachGateway(gateway.name(), created.subscriptionId(), created.status(), now);
        });
        User owner = users.getById(ownerId);
        long total = plan.getPricePaise() + GstSplit.gstOn(plan.getPricePaise(), properties.gstRateBps());
        return new SubscriptionCheckoutResponse(gateway.name(), gateway.publicKeyId(), created.subscriptionId(), created.shortUrl(),
                paymentProperties.merchantName(), plan.getName() + " plan for " + restaurant.getName() + " (monthly, incl. GST)", total,
                owner.getName(), owner.getPhone());
    }

    /** The checkout handler's result: verified, then the mandate counts as set up (charges arrive by webhook). */
    public BillingOverviewResponse confirmCheckout(UUID ownerId, UUID restaurantId, ConfirmSubscriptionRequest request) {
        tx.executeWithoutResult(s -> access.requireOwned(restaurantId, ownerId));
        RestaurantSubscription current = tx.execute(s -> subscription(restaurantId));
        if (!request.subscriptionId().equals(current.getProviderSubscriptionId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This payment is for a different subscription");
        }
        if (!gateway.verifyCheckout(request.subscriptionId(), request.paymentId(), request.signature() == null ? "" : request.signature())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment signature is invalid");
        }
        tx.executeWithoutResult(s -> subscriptions.lock(restaurantId).orElseThrow().paymentAuthorised(clock.instant()));
        return overview(ownerId, restaurantId);
    }

    /** Upgrades take effect now; downgrades at the end of the paid period (so nothing paid for is taken away). */
    public BillingOverviewResponse changePlan(UUID ownerId, UUID restaurantId, UUID planId) {
        tx.executeWithoutResult(s -> access.requireOwned(restaurantId, ownerId));
        Plan target = activePlan(planId);
        requireFits(restaurantId, target);
        RestaurantSubscription current = tx.execute(s -> subscription(restaurantId));
        Plan plan = plans.findById(current.getPlanId()).orElseThrow();
        if (plan.getId().equals(target.getId())) {
            if (current.getPendingPlanId() != null) {
                tx.executeWithoutResult(s -> subscriptions.lock(restaurantId).orElseThrow().changePlan(plan.getId(), clock.instant()));
            }
            return overview(ownerId, restaurantId);
        }
        boolean live = current.isPaymentMethodReady() && current.getProviderSubscriptionId() != null && current.getCancelledAt() == null;
        boolean downgrade = target.getPricePaise() < plan.getPricePaise();
        if (live) {
            gateway.changePlan(current.getProviderSubscriptionId(), gatewayPlan(target), downgrade);
        }
        tx.executeWithoutResult(s -> {
            RestaurantSubscription sub = subscriptions.lock(restaurantId).orElseThrow();
            if (live && downgrade && sub.getStatus() == SubscriptionStatus.ACTIVE) {
                sub.schedulePlan(target.getId(), clock.instant());
            } else {
                sub.changePlan(target.getId(), clock.instant());
            }
        });
        return overview(ownerId, restaurantId);
    }

    /** Cancels at the end of the paid period; the listing stays until then. */
    public BillingOverviewResponse cancel(UUID ownerId, UUID restaurantId) {
        tx.executeWithoutResult(s -> access.requireOwned(restaurantId, ownerId));
        RestaurantSubscription current = tx.execute(s -> subscription(restaurantId));
        if (current.getProviderSubscriptionId() == null || !current.isPaymentMethodReady()) {
            throw new ApiException(HttpStatus.CONFLICT, "There is no recurring payment to cancel");
        }
        gateway.cancel(current.getProviderSubscriptionId(), true);
        tx.executeWithoutResult(s -> subscriptions.lock(restaurantId).orElseThrow().cancelRequested(clock.instant()));
        if ("mock".equals(gateway.name())) {
            applyCancelled(gateway.name(), current.getProviderSubscriptionId(), "cancelled");
        }
        return overview(ownerId, restaurantId);
    }

    // ── gateway events (webhooks or the mock simulator); idempotent ─────────

    /** The mandate was authorised (also confirmed by the checkout handler). */
    public boolean applyAuthenticated(String provider, String subscriptionId) {
        return withSubscription(provider, subscriptionId, sub -> sub.paymentAuthorised(clock.instant()));
    }

    public boolean applyActivated(String provider, String subscriptionId, Instant periodStart, Instant periodEnd) {
        return withSubscription(provider, subscriptionId, sub -> {
            sub.paid(periodStart, periodEnd, "active", clock.instant());
            restaurantService.reinstateAfterPayment(sub.getRestaurantId());
        });
    }

    /** A successful charge: paid up, invoiced, listed again if billing had hidden it. */
    public boolean applyCharged(String provider, String subscriptionId, String paymentId, long amountPaise, Instant periodStart, Instant periodEnd) {
        return withSubscription(provider, subscriptionId, sub -> {
            sub.paid(periodStart, periodEnd, "active", clock.instant());
            Plan plan = plans.findById(sub.getPlanId()).orElseThrow();
            Restaurant restaurant = restaurants.findById(sub.getRestaurantId()).orElseThrow();
            if (invoices.issue(restaurant, plan, paymentId, amountPaise, periodStart, periodEnd)) {
                log.info("Restaurant {} charged {} paise for {} (payment {})", restaurant.getId(), amountPaise, plan.getCode(), paymentId);
            }
            restaurantService.reinstateAfterPayment(sub.getRestaurantId());
        });
    }

    /** A charge failed (Razorpay retries: subscription.pending, then subscription.halted). Grace starts once. */
    public boolean applyPaymentFailed(String provider, String subscriptionId, String providerStatus) {
        return withSubscription(provider, subscriptionId, sub -> sub.pastDue(graceFrom(clock.instant()), providerStatus, clock.instant()));
    }

    public boolean applyCancelled(String provider, String subscriptionId, String providerStatus) {
        return withSubscription(provider, subscriptionId, sub -> sub.cancelled(providerStatus, clock.instant()));
    }

    private boolean withSubscription(String provider, String subscriptionId, java.util.function.Consumer<RestaurantSubscription> change) {
        Boolean known = tx.execute(s -> {
            Optional<RestaurantSubscription> sub = subscriptions.lockByGateway(provider, subscriptionId);
            sub.ifPresent(change);
            return sub.isPresent();
        });
        return Boolean.TRUE.equals(known);
    }

    // ── enforcement ──────────────────────────────────────────────────────────

    /** Hourly: unpaid trials go into grace; grace that ran out (or a cancelled plan past its period) hides the listing. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    public void enforce() {
        Instant now = clock.instant();
        for (UUID restaurantId : subscriptions.needingEnforcement(now)) {
            tx.executeWithoutResult(s -> subscriptions.lock(restaurantId).ifPresent(sub -> {
                switch (sub.getStatus()) {
                    case TRIALING -> {
                        if (!sub.isPaymentMethodReady() && sub.getTrialEndsAt().isBefore(now)) {
                            sub.pastDue(graceFrom(sub.getTrialEndsAt()), null, now);
                            log.info("Restaurant {} trial ended without payment; grace until {}", restaurantId, sub.getGraceUntil());
                        }
                    }
                    case PAST_DUE -> {
                        if (sub.getGraceUntil() != null && sub.getGraceUntil().isBefore(now)) {
                            suspend(sub, now);
                        }
                    }
                    case CANCELLED -> {
                        if (sub.getCurrentPeriodEnd() == null || sub.getCurrentPeriodEnd().isBefore(now)) {
                            suspend(sub, now);
                        }
                    }
                    default -> {
                    }
                }
            }));
        }
    }

    private void suspend(RestaurantSubscription sub, Instant now) {
        sub.suspended(now);
        if (restaurantService.suspendForBilling(sub.getRestaurantId())) {
            log.info("Restaurant {} hidden for unpaid billing (data kept)", sub.getRestaurantId());
        }
    }

    private Instant graceFrom(Instant start) {
        return start.plus(Duration.ofDays(settings.getInt(GRACE_DAYS, properties.defaultGraceDays())));
    }

    // ── plan limits ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public OptionalInt maxStations(UUID restaurantId) {
        return subscriptions.findById(restaurantId).flatMap(s -> plans.findById(s.getPlanId()))
                .map(p -> OptionalInt.of(p.getFeatures().maxStations())).orElse(OptionalInt.empty());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Duration> clipRetention(UUID restaurantId) {
        return subscriptions.findById(restaurantId).flatMap(s -> plans.findById(s.getPlanId()))
                .map(p -> Duration.ofDays(p.getFeatures().clipRetentionDays()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    RestaurantSubscription subscription(UUID restaurantId) {
        return subscriptions.findById(restaurantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This restaurant has no subscription"));
    }

    private Plan activePlan(UUID planId) {
        return plans.findById(planId).filter(Plan::isActive).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan not found"));
    }

    private void requireFits(UUID restaurantId, Plan plan) {
        long count = Optional.ofNullable(tx.execute(s -> (long) stations.findByRestaurantIdOrderByNameAsc(restaurantId).size())).orElse(0L);
        if (count > plan.getFeatures().maxStations()) {
            throw new ApiException(HttpStatus.CONFLICT, "The " + plan.getName() + " plan allows " + plan.getFeatures().maxStations()
                    + " stations; you have " + count + ". Remove some first.");
        }
    }

    /** The gateway plan for the plan's current price incl. GST (created on first use, and again after a price change). */
    private String gatewayPlan(Plan plan) {
        long total = plan.getPricePaise() + GstSplit.gstOn(plan.getPricePaise(), properties.gstRateBps());
        if (plan.hasGatewayPlan(gateway.name(), total)) {
            return plan.getProviderPlanId();
        }
        String id = gateway.createPlan("Virtual Live Kitchen " + plan.getName(), total, plan.getName() + " plan, monthly, incl. GST");
        tx.executeWithoutResult(s -> plans.findById(plan.getId()).orElseThrow().attachGatewayPlan(gateway.name(), id, total));
        return id;
    }

    PlanResponse toResponse(Plan plan) {
        long gst = GstSplit.gstOn(plan.getPricePaise(), properties.gstRateBps());
        return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getPricePaise(), gst, plan.getPricePaise() + gst,
                plan.getFeatures(), plan.isActive(), plan.getSortOrder());
    }
}
