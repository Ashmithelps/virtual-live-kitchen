-- A chef's payout destination: a Razorpay Route linked account. Bank account, IFSC and PAN are sent to the
-- gateway and never stored; only masked fragments for display. No row = NOT_SET_UP.
CREATE TABLE chef_payout_accounts (
    chef_id              UUID         PRIMARY KEY REFERENCES chefs (id),
    provider             VARCHAR(20)  NOT NULL,
    provider_account_id  VARCHAR(64)  NOT NULL UNIQUE,
    provider_product_id  VARCHAR(64),
    status               VARCHAR(20)  NOT NULL,
    provider_status      VARCHAR(40),
    legal_name           VARCHAR(120) NOT NULL,
    bank_account_last4   VARCHAR(4)   NOT NULL,
    bank_code            VARCHAR(4)   NOT NULL,
    pan_last4            VARCHAR(4)   NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    activated_at         TIMESTAMPTZ,
    CONSTRAINT ck_chef_payout_status CHECK (status IN ('PENDING_KYC', 'ACTIVE'))
);

-- One tip = one gateway order whose whole chef share is transferred to the chef's linked account on capture.
CREATE TABLE tips (
    id                    UUID         PRIMARY KEY,
    order_id              UUID         NOT NULL REFERENCES orders (id),
    chef_id               UUID         NOT NULL REFERENCES chefs (id),
    customer_id           UUID         NOT NULL REFERENCES users (id),
    source                VARCHAR(20)  NOT NULL,
    amount_paise          BIGINT       NOT NULL,
    platform_fee_paise    BIGINT       NOT NULL,
    chef_amount_paise     BIGINT       NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    provider              VARCHAR(20)  NOT NULL,
    provider_order_id     VARCHAR(64)  UNIQUE,
    provider_payment_id   VARCHAR(64),
    provider_transfer_id  VARCHAR(64),
    linked_account_id     VARCHAR(64)  NOT NULL,
    failure_reason        VARCHAR(300),
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    paid_at               TIMESTAMPTZ,
    transferred_at        TIMESTAMPTZ,
    CONSTRAINT ck_tips_status CHECK (status IN ('CREATED', 'PAID', 'TRANSFERRED', 'FAILED')),
    CONSTRAINT ck_tips_source CHECK (source IN ('CLIP', 'CHECKOUT')),
    CONSTRAINT ck_tips_amounts CHECK (amount_paise > 0 AND platform_fee_paise >= 0
        AND chef_amount_paise > 0 AND chef_amount_paise + platform_fee_paise = amount_paise)
);

CREATE INDEX idx_tips_chef ON tips (chef_id, created_at DESC);
CREATE INDEX idx_tips_order ON tips (order_id);
CREATE INDEX idx_tips_payment ON tips (provider_payment_id);

CREATE TABLE chef_follows (
    customer_id  UUID        NOT NULL REFERENCES users (id),
    chef_id      UUID        NOT NULL REFERENCES chefs (id),
    created_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (customer_id, chef_id)
);

CREATE INDEX idx_chef_follows_chef ON chef_follows (chef_id);
