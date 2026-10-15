-- Subscription plans. Prices (paise, excluding GST) and features are edited by admins, never hard-coded.
CREATE TABLE plans (
    id                 UUID          PRIMARY KEY,
    code               VARCHAR(40)   NOT NULL UNIQUE,
    name               VARCHAR(80)   NOT NULL,
    price_paise        BIGINT        NOT NULL,
    -- {"maxStations": 2, "clipRetentionDays": 30, "analytics": false}
    features           JSONB         NOT NULL,
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order         INTEGER       NOT NULL DEFAULT 0,
    -- Gateway plan for the current price (Razorpay plan amounts are immutable; a price change gets a new one).
    provider           VARCHAR(20),
    provider_plan_id   VARCHAR(64),
    provider_amount_paise BIGINT,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_plans_price CHECK (price_paise >= 0)
);

INSERT INTO plans (id, code, name, price_paise, features, sort_order, created_at, updated_at) VALUES
    (gen_random_uuid(), 'starter', 'Starter', 99900,  '{"maxStations": 2, "clipRetentionDays": 30, "analytics": false}', 0, now(), now()),
    (gen_random_uuid(), 'growth',  'Growth',  249900, '{"maxStations": 5, "clipRetentionDays": 60, "analytics": true}',  1, now(), now()),
    (gen_random_uuid(), 'pro',     'Pro',     499900, '{"maxStations": 20, "clipRetentionDays": 90, "analytics": true}', 2, now(), now());

-- One subscription per restaurant. Status: TRIALING → ACTIVE; PAST_DUE (in grace after a failed charge or an expired
-- trial) → SUSPENDED (listing hidden, data kept); CANCELLED (runs to the period end, then suspended).
CREATE TABLE restaurant_subscriptions (
    restaurant_id             UUID         PRIMARY KEY REFERENCES restaurants (id),
    plan_id                   UUID         NOT NULL REFERENCES plans (id),
    -- A downgrade waits for the end of the paid period.
    pending_plan_id           UUID         REFERENCES plans (id),
    status                    VARCHAR(20)  NOT NULL,
    trial_ends_at             TIMESTAMPTZ  NOT NULL,
    current_period_start      TIMESTAMPTZ,
    current_period_end        TIMESTAMPTZ,
    grace_until               TIMESTAMPTZ,
    provider                  VARCHAR(20),
    provider_subscription_id  VARCHAR(64)  UNIQUE,
    provider_status           VARCHAR(40),
    payment_method_ready      BOOLEAN      NOT NULL DEFAULT FALSE,
    cancelled_at              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_subscription_status CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED'))
);

-- GST tax invoices, one per successful charge, numbered sequentially per financial year (GST rule).
CREATE TABLE invoice_counters (
    financial_year  VARCHAR(9)  PRIMARY KEY,
    last_number     INTEGER     NOT NULL
);

CREATE TABLE invoices (
    id                   UUID          PRIMARY KEY,
    number               VARCHAR(40)   NOT NULL UNIQUE,
    restaurant_id        UUID          NOT NULL REFERENCES restaurants (id),
    plan_id              UUID          NOT NULL REFERENCES plans (id),
    plan_name            VARCHAR(80)   NOT NULL,
    period_start         TIMESTAMPTZ,
    period_end           TIMESTAMPTZ,
    taxable_paise        BIGINT        NOT NULL,
    cgst_paise           BIGINT        NOT NULL,
    sgst_paise           BIGINT        NOT NULL,
    igst_paise           BIGINT        NOT NULL,
    total_paise          BIGINT        NOT NULL,
    gst_rate_bps         INTEGER       NOT NULL,
    -- Buyer snapshot at issue time (an invoice never changes afterwards).
    buyer_legal_name     VARCHAR(160)  NOT NULL,
    buyer_gstin          VARCHAR(15),
    buyer_address        VARCHAR(400)  NOT NULL,
    place_of_supply      VARCHAR(2)    NOT NULL,
    provider_payment_id  VARCHAR(64)   UNIQUE,
    issued_at            TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_invoice_total CHECK (total_paise = taxable_paise + cgst_paise + sgst_paise + igst_paise)
);

CREATE INDEX idx_invoices_restaurant ON invoices (restaurant_id, issued_at DESC);

-- Compliance and billing details on the restaurant; why it's suspended (an admin, or unpaid billing).
ALTER TABLE restaurants
    ADD COLUMN fssai_number       VARCHAR(14),
    ADD COLUMN fssai_doc_key      VARCHAR(300),
    ADD COLUMN legal_name         VARCHAR(160),
    ADD COLUMN gstin              VARCHAR(15),
    ADD COLUMN billing_state_code VARCHAR(2),
    ADD COLUMN suspended_reason   VARCHAR(20);

-- Every admin action: who, what, on what, when.
CREATE TABLE admin_audit_log (
    id           UUID          PRIMARY KEY,
    admin_id     UUID          NOT NULL REFERENCES users (id),
    action       VARCHAR(60)   NOT NULL,
    target_type  VARCHAR(40)   NOT NULL,
    target_id    VARCHAR(64),
    details      JSONB         NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_admin_audit_created ON admin_audit_log (created_at DESC);

-- Existing restaurants start a trial on the cheapest plan (30 days from now).
INSERT INTO restaurant_subscriptions (restaurant_id, plan_id, status, trial_ends_at, created_at, updated_at)
SELECT r.id, (SELECT id FROM plans ORDER BY sort_order LIMIT 1), 'TRIALING', now() + interval '30 days', now(), now()
FROM restaurants r;
