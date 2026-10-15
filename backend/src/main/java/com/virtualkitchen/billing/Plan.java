package com.virtualkitchen.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    /** Monthly price in paise, excluding GST. */
    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private PlanFeatures features;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(length = 20)
    private String provider;

    @Column(name = "provider_plan_id", length = 64)
    private String providerPlanId;

    @Column(name = "provider_amount_paise")
    private Long providerAmountPaise;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Plan() {
    }

    Plan(String code, String name, long pricePaise, PlanFeatures features, int sortOrder, Instant now) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.createdAt = now;
        update(name, pricePaise, features, true, sortOrder, now);
    }

    void update(String name, long pricePaise, PlanFeatures features, boolean active, int sortOrder, Instant now) {
        this.name = name;
        this.pricePaise = pricePaise;
        this.features = features;
        this.active = active;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    /** The gateway plan for the current price, if one was created already. */
    boolean hasGatewayPlan(String providerName, long amountPaise) {
        return providerPlanId != null && providerName.equals(provider) && providerAmountPaise != null && providerAmountPaise == amountPaise;
    }

    void attachGatewayPlan(String providerName, String planId, long amountPaise) {
        this.provider = providerName;
        this.providerPlanId = planId;
        this.providerAmountPaise = amountPaise;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public long getPricePaise() { return pricePaise; }
    public PlanFeatures getFeatures() { return features; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public String getProviderPlanId() { return providerPlanId; }
}
