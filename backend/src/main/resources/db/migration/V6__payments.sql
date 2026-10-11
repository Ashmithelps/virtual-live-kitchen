-- Milestone 8: real payments (Razorpay), payment attempts table, idempotent webhooks.

-- One row per payment attempt or counter settlement. An order can have several attempts
-- (e.g. a failed card, then UPI); at most one ends up CAPTURED.
CREATE TABLE payments (
    id                  UUID         PRIMARY KEY,
    order_id            UUID         NOT NULL REFERENCES orders (id),
    -- mock | razorpay | counter (cash/UPI/card taken by staff)
    provider            VARCHAR(32)  NOT NULL,
    provider_order_id   VARCHAR(100),
    provider_payment_id VARCHAR(100),
    provider_refund_id  VARCHAR(100),
    amount_paise        BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL DEFAULT 'INR',
    status              VARCHAR(20)  NOT NULL,
    counter_method      VARCHAR(10),
    recorded_by         UUID         REFERENCES users (id),
    failure_reason      VARCHAR(300),
    -- Latest provider webhook body that touched this payment (full history: payment_webhook_events).
    raw_webhook_payload JSONB,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    captured_at         TIMESTAMPTZ,
    refund_requested_at TIMESTAMPTZ,
    refunded_at         TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_payments_provider_order UNIQUE (provider, provider_order_id),
    CONSTRAINT ck_payments_amount CHECK (amount_paise > 0),
    CONSTRAINT ck_payments_status CHECK (status IN
        ('CREATED', 'CAPTURED', 'FAILED', 'REFUND_REQUESTED', 'REFUND_PENDING', 'REFUNDED')),
    CONSTRAINT ck_payments_counter CHECK ((provider = 'counter') = (counter_method IS NOT NULL)),
    CONSTRAINT ck_payments_counter_method CHECK (counter_method IS NULL OR counter_method IN ('CASH', 'UPI', 'CARD'))
);

CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_payments_provider_payment_id ON payments (provider_payment_id);
CREATE INDEX idx_payments_refund_requested ON payments (refund_requested_at) WHERE status = 'REFUND_REQUESTED';

-- Every webhook delivery we accepted. The primary key makes processing idempotent: a redelivered
-- event id is acknowledged without being applied twice.
CREATE TABLE payment_webhook_events (
    provider    VARCHAR(32)  NOT NULL,
    event_id    VARCHAR(100) NOT NULL,
    event_type  VARCHAR(64)  NOT NULL,
    payload     JSONB        NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (provider, event_id)
);

-- Carry existing (mock) payment data over from the order columns, then drop them.
INSERT INTO payments (id, order_id, provider, provider_order_id, provider_payment_id, provider_refund_id,
                      amount_paise, status, created_at, updated_at, captured_at, refunded_at)
SELECT gen_random_uuid(), id, payment_provider, payment_provider_order_id, payment_provider_payment_id,
       payment_provider_refund_id, total_paise,
       CASE payment_status WHEN 'PAID' THEN 'CAPTURED' WHEN 'REFUNDED' THEN 'REFUNDED'
                           WHEN 'FAILED' THEN 'FAILED' ELSE 'CREATED' END,
       placed_at, updated_at, paid_at, refunded_at
FROM orders
WHERE payment_provider IS NOT NULL;

ALTER TABLE orders
    DROP COLUMN payment_provider,
    DROP COLUMN payment_provider_order_id,
    DROP COLUMN payment_provider_payment_id,
    DROP COLUMN payment_provider_refund_id;

-- Real refunds are asynchronous: requested → (webhook) → refunded.
ALTER TABLE orders DROP CONSTRAINT ck_orders_payment_status;
ALTER TABLE orders ADD CONSTRAINT ck_orders_payment_status
    CHECK (payment_status IN ('PENDING', 'PAID', 'FAILED', 'PAY_LATER', 'REFUND_PENDING', 'REFUNDED'));
