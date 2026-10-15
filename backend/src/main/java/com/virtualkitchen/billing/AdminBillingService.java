package com.virtualkitchen.billing;

import com.virtualkitchen.admin.AdminAudit;
import com.virtualkitchen.billing.dto.AdminSubscriptionResponse;
import com.virtualkitchen.billing.dto.BillingSettingsResponse;
import com.virtualkitchen.billing.dto.PlanRequest;
import com.virtualkitchen.billing.dto.PlanResponse;
import com.virtualkitchen.billing.dto.SimulateRequest;
import com.virtualkitchen.common.crypto.SecureTokens;
import com.virtualkitchen.common.error.ApiException;
import com.virtualkitchen.common.settings.PlatformSettings;
import com.virtualkitchen.payment.SubscriptionProvider;
import com.virtualkitchen.restaurant.RestaurantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin side of billing: plans and prices, trial/grace settings, all subscriptions, and the mock-gateway simulator. */
@Service
public class AdminBillingService {

    private final PlanRepository plans;
    private final RestaurantSubscriptionRepository subscriptions;
    private final RestaurantRepository restaurants;
    private final BillingService billing;
    private final SubscriptionProvider gateway;
    private final PlatformSettings settings;
    private final BillingProperties properties;
    private final AdminAudit audit;
    private final Clock clock;

    public AdminBillingService(PlanRepository plans, RestaurantSubscriptionRepository subscriptions, RestaurantRepository restaurants,
                               BillingService billing, SubscriptionProvider gateway, PlatformSettings settings, BillingProperties properties,
                               AdminAudit audit, Clock clock) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.restaurants = restaurants;
        this.billing = billing;
        this.gateway = gateway;
        this.settings = settings;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> plans() {
        return plans.findAllByOrderBySortOrderAscPricePaiseAsc().stream().map(billing::toResponse).toList();
    }

    @Transactional
    public PlanResponse createPlan(UUID adminId, PlanRequest request) {
        if (request.code() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "code is required for a new plan");
        if (plans.findByCode(request.code()).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "A plan with code " + request.code() + " exists");
        Plan plan = new Plan(request.code(), request.name().strip(), request.pricePaise(), request.features(), request.sortOrder(), clock.instant());
        plan.update(plan.getName(), plan.getPricePaise(), plan.getFeatures(), request.active(), request.sortOrder(), clock.instant());
        plans.saveAndFlush(plan);
        audit.record(adminId, "PLAN_CREATED", "plan", plan.getId(), Map.of("code", plan.getCode(), "pricePaise", plan.getPricePaise(),
                "features", plan.getFeatures()));
        return billing.toResponse(plan);
    }

    /**
     * New prices apply to new subscriptions and plan changes (a new gateway plan is created on first use); existing
     * mandates keep the amount they authorised until the owner changes plan.
     */
    @Transactional
    public PlanResponse updatePlan(UUID adminId, UUID planId, PlanRequest request) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan not found"));
        if (!request.active() && plans.findByActiveTrueOrderBySortOrderAscPricePaiseAsc().stream().allMatch(p -> p.getId().equals(planId))) {
            throw new ApiException(HttpStatus.CONFLICT, "At least one plan must stay active");
        }
        Map<String, Object> before = Map.of("name", plan.getName(), "pricePaise", plan.getPricePaise(), "features", plan.getFeatures(), "active", plan.isActive());
        plan.update(request.name().strip(), request.pricePaise(), request.features(), request.active(), request.sortOrder(), clock.instant());
        audit.record(adminId, "PLAN_UPDATED", "plan", plan.getId(), Map.of("before", before, "after",
                Map.of("name", plan.getName(), "pricePaise", plan.getPricePaise(), "features", plan.getFeatures(), "active", plan.isActive())));
        return billing.toResponse(plan);
    }

    @Transactional(readOnly = true)
    public BillingSettingsResponse settings() {
        return new BillingSettingsResponse(settings.getInt(BillingService.TRIAL_DAYS, properties.defaultTrialDays()),
                settings.getInt(BillingService.GRACE_DAYS, properties.defaultGraceDays()), properties.gstRateBps(), gateway.name(),
                "mock".equals(gateway.name()));
    }

    @Transactional
    public BillingSettingsResponse updateSettings(UUID adminId, int trialDays, int graceDays) {
        BillingSettingsResponse before = settings();
        settings.set(BillingService.TRIAL_DAYS, Integer.toString(trialDays), adminId);
        settings.set(BillingService.GRACE_DAYS, Integer.toString(graceDays), adminId);
        audit.record(adminId, "BILLING_SETTINGS_UPDATED", "platform", null, Map.of("trialDays", Map.of("from", before.trialDays(), "to", trialDays),
                "graceDays", Map.of("from", before.graceDays(), "to", graceDays)));
        return settings();
    }

    @Transactional(readOnly = true)
    public List<AdminSubscriptionResponse> subscriptions() {
        Map<UUID, Plan> byId = new java.util.HashMap<>();
        plans.findAll().forEach(p -> byId.put(p.getId(), p));
        return subscriptions.findAll().stream().map(s -> {
            Plan plan = byId.get(s.getPlanId());
            String name = restaurants.findById(s.getRestaurantId()).map(r -> r.getName()).orElse("?");
            return new AdminSubscriptionResponse(s.getRestaurantId(), name, s.getStatus(), plan.getName(), plan.getPricePaise(), s.getTrialEndsAt(),
                    s.getCurrentPeriodEnd(), s.getGraceUntil(), s.isPaymentMethodReady(), s.getProviderSubscriptionId());
        }).sorted(java.util.Comparator.comparing(AdminSubscriptionResponse::restaurantName)).toList();
    }

    @Transactional(readOnly = true)
    public AdminSubscriptionResponse subscription(UUID restaurantId) {
        return subscriptions().stream().filter(s -> s.restaurantId().equals(restaurantId)).findFirst().orElse(null);
    }

    /** Mock gateway only: acts as if Razorpay sent this webhook for the restaurant's subscription. */
    public AdminSubscriptionResponse simulate(UUID adminId, UUID restaurantId, SimulateRequest.Event event) {
        if (!"mock".equals(gateway.name())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Simulation is only available with the mock payment provider");
        }
        RestaurantSubscription sub = billing.subscription(restaurantId);
        String sid = sub.getProviderSubscriptionId();
        if (sid == null) {
            throw new ApiException(HttpStatus.CONFLICT, "The owner hasn't set up payments yet (Billing → Set up payment)");
        }
        Instant now = clock.instant();
        Instant monthLater = now.atZone(ZoneId.of("Asia/Kolkata")).plusMonths(1).toInstant();
        Plan plan = plans.findById(sub.getPlanId()).orElseThrow();
        switch (event) {
            case ACTIVATED -> billing.applyActivated("mock", sid, now, monthLater);
            case CHARGED -> billing.applyCharged("mock", sid, "pay_mock_" + SecureTokens.random(9),
                    plan.getPricePaise() + GstSplit.gstOn(plan.getPricePaise(), properties.gstRateBps()), now, monthLater);
            case PENDING -> billing.applyPaymentFailed("mock", sid, "pending");
            case HALTED -> billing.applyPaymentFailed("mock", sid, "halted");
            case CANCELLED -> billing.applyCancelled("mock", sid, "cancelled");
        }
        audit.record(adminId, "BILLING_SIMULATED", "restaurant", restaurantId, Map.of("event", event.name()));
        return subscription(restaurantId);
    }
}
