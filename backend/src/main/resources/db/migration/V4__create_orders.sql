-- Milestone 4: orders, order items, per-restaurant daily display numbers.

-- One row per restaurant per business day (Asia/Kolkata). Incremented atomically with
-- INSERT ... ON CONFLICT DO UPDATE ... RETURNING, which row-locks until the order commits.
CREATE TABLE order_daily_counters (
    restaurant_id UUID    NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    business_date DATE    NOT NULL,
    last_number   INTEGER NOT NULL,
    PRIMARY KEY (restaurant_id, business_date)
);

CREATE TABLE orders (
    id                          UUID             PRIMARY KEY,
    restaurant_id               UUID             NOT NULL REFERENCES restaurants (id),
    customer_id                 UUID             NOT NULL REFERENCES users (id),
    order_type                  VARCHAR(16)      NOT NULL,
    business_date               DATE             NOT NULL,
    display_number              INTEGER          NOT NULL,

    -- dine-in: table is kept as FK + label snapshot (the table may later be deleted)
    table_id                    UUID             REFERENCES dining_tables (id) ON DELETE SET NULL,
    table_label                 VARCHAR(20),

    -- delivery
    delivery_address            VARCHAR(500),
    delivery_latitude           DOUBLE PRECISION,
    delivery_longitude          DOUBLE PRECISION,
    delivery_distance_km        DOUBLE PRECISION,

    status                      VARCHAR(24)      NOT NULL,
    payment_method              VARCHAR(16)      NOT NULL,
    payment_status              VARCHAR(16)      NOT NULL,
    payment_provider            VARCHAR(32),
    payment_provider_order_id   VARCHAR(100),
    payment_provider_payment_id VARCHAR(100),
    payment_provider_refund_id  VARCHAR(100),

    subtotal_paise              BIGINT           NOT NULL,
    tax_paise                   BIGINT           NOT NULL,
    total_paise                 BIGINT           NOT NULL,
    notes                       VARCHAR(500),
    status_reason               VARCHAR(300),

    -- set when the order moves to PREPARING
    prepared_by_chef_id         UUID             REFERENCES chefs (id),
    station_id                  UUID             REFERENCES kitchen_stations (id) ON DELETE SET NULL,

    placed_at                   TIMESTAMPTZ      NOT NULL,
    paid_at                     TIMESTAMPTZ,
    accepted_at                 TIMESTAMPTZ,
    rejected_at                 TIMESTAMPTZ,
    preparing_at                TIMESTAMPTZ,
    ready_at                    TIMESTAMPTZ,
    served_at                   TIMESTAMPTZ,
    picked_up_at                TIMESTAMPTZ,
    out_for_delivery_at         TIMESTAMPTZ,
    delivered_at                TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,
    refunded_at                 TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ      NOT NULL,
    version                     BIGINT           NOT NULL DEFAULT 0,

    CONSTRAINT uq_orders_display_number UNIQUE (restaurant_id, business_date, display_number),
    CONSTRAINT ck_orders_order_type CHECK (order_type IN ('DINE_IN', 'TAKEAWAY', 'DELIVERY')),
    CONSTRAINT ck_orders_status CHECK (status IN ('PLACED', 'ACCEPTED', 'REJECTED', 'PREPARING', 'READY',
        'SERVED', 'PICKED_UP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_payment_method CHECK (payment_method IN ('ONLINE', 'PAY_LATER')),
    CONSTRAINT ck_orders_payment_status CHECK (payment_status IN ('PENDING', 'PAID', 'FAILED', 'PAY_LATER', 'REFUNDED')),
    CONSTRAINT ck_orders_pay_later_dine_in CHECK (payment_method <> 'PAY_LATER' OR order_type = 'DINE_IN'),
    CONSTRAINT ck_orders_dine_in_table CHECK ((order_type = 'DINE_IN') = (table_label IS NOT NULL)),
    CONSTRAINT ck_orders_delivery_address CHECK ((order_type = 'DELIVERY') = (delivery_address IS NOT NULL
        AND delivery_latitude IS NOT NULL AND delivery_longitude IS NOT NULL)),
    CONSTRAINT ck_orders_amounts CHECK (subtotal_paise > 0 AND tax_paise >= 0 AND total_paise = subtotal_paise + tax_paise),
    CONSTRAINT ck_orders_display_number CHECK (display_number > 0)
);

CREATE INDEX idx_orders_restaurant_status ON orders (restaurant_id, status);
CREATE INDEX idx_orders_customer_placed ON orders (customer_id, placed_at DESC);

CREATE TABLE order_items (
    id               UUID         PRIMARY KEY,
    order_id         UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    -- snapshot below is authoritative; the menu item may later change or be deleted
    menu_item_id     UUID         REFERENCES menu_items (id) ON DELETE SET NULL,
    item_name        VARCHAR(120) NOT NULL,
    unit_price_paise BIGINT       NOT NULL,
    is_veg           BOOLEAN      NOT NULL,
    quantity         INTEGER      NOT NULL,
    line_total_paise BIGINT       NOT NULL,
    notes            VARCHAR(200),
    line_number      INTEGER      NOT NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_line_total CHECK (line_total_paise = unit_price_paise * quantity)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
