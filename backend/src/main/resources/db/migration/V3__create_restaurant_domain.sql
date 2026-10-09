-- Milestone 3: restaurants, menu, tables, kitchen stations, chefs, recording consent.

-- Chefs removed by their owner are DISABLED (kept for consent history and future clips).
ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status
    CHECK (status IN ('ACTIVE', 'PENDING_APPROVAL', 'DISABLED'));

CREATE TABLE restaurants (
    id                 UUID             PRIMARY KEY,
    owner_id           UUID             NOT NULL REFERENCES users (id),
    name               VARCHAR(120)     NOT NULL,
    slug               VARCHAR(80)      NOT NULL,
    address            VARCHAR(300)     NOT NULL,
    city               VARCHAR(80)      NOT NULL,
    latitude           DOUBLE PRECISION NOT NULL,
    longitude          DOUBLE PRECISION NOT NULL,
    -- lower-case tags, e.g. {north-indian,biryani}
    cuisine_tags       TEXT[]           NOT NULL DEFAULT '{}',
    supports_dine_in   BOOLEAN          NOT NULL,
    supports_takeaway  BOOLEAN          NOT NULL,
    supports_delivery  BOOLEAN          NOT NULL,
    delivery_radius_km DOUBLE PRECISION,
    status             VARCHAR(32)      NOT NULL,
    created_at         TIMESTAMPTZ      NOT NULL,
    updated_at         TIMESTAMPTZ      NOT NULL,
    CONSTRAINT uq_restaurants_slug UNIQUE (slug),
    CONSTRAINT ck_restaurants_status CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_restaurants_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_restaurants_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_restaurants_delivery_radius
        CHECK (NOT supports_delivery OR (delivery_radius_km IS NOT NULL AND delivery_radius_km > 0))
);

CREATE INDEX idx_restaurants_owner_id ON restaurants (owner_id);
CREATE INDEX idx_restaurants_city_status ON restaurants (lower(city), status);
CREATE INDEX idx_restaurants_cuisine_tags ON restaurants USING GIN (cuisine_tags);

-- Wall-clock times in the platform timezone (Asia/Kolkata). closes_at <= opens_at means the
-- slot runs past midnight into the next day (equal = open 24 hours).
CREATE TABLE restaurant_opening_hours (
    restaurant_id UUID       NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    day_of_week   VARCHAR(9) NOT NULL,
    opens_at      TIME       NOT NULL,
    closes_at     TIME       NOT NULL,
    CONSTRAINT ck_opening_hours_day CHECK (day_of_week IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'))
);

CREATE INDEX idx_opening_hours_restaurant_id ON restaurant_opening_hours (restaurant_id);

CREATE TABLE menu_categories (
    id            UUID         PRIMARY KEY,
    restaurant_id UUID         NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    name          VARCHAR(80)  NOT NULL,
    description   VARCHAR(300),
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_menu_categories_restaurant_name UNIQUE (restaurant_id, name),
    -- target of the composite FK below
    CONSTRAINT uq_menu_categories_id_restaurant UNIQUE (id, restaurant_id)
);

CREATE TABLE menu_items (
    id            UUID         PRIMARY KEY,
    restaurant_id UUID         NOT NULL,
    category_id   UUID         NOT NULL,
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(500),
    price_paise   BIGINT       NOT NULL,
    is_veg        BOOLEAN      NOT NULL,
    image_key     VARCHAR(300),
    is_available  BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_menu_items_price CHECK (price_paise > 0),
    -- an item's category must belong to the same restaurant
    CONSTRAINT fk_menu_items_category FOREIGN KEY (category_id, restaurant_id)
        REFERENCES menu_categories (id, restaurant_id),
    CONSTRAINT fk_menu_items_restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurants (id) ON DELETE CASCADE
);

CREATE INDEX idx_menu_items_restaurant_id ON menu_items (restaurant_id);
CREATE INDEX idx_menu_items_category_id ON menu_items (category_id);

CREATE TABLE dining_tables (
    id            UUID        PRIMARY KEY,
    restaurant_id UUID        NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    label         VARCHAR(20) NOT NULL,
    qr_token      VARCHAR(64) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_dining_tables_restaurant_label UNIQUE (restaurant_id, label),
    CONSTRAINT uq_dining_tables_qr_token UNIQUE (qr_token)
);

CREATE TABLE kitchen_stations (
    id                UUID        PRIMARY KEY,
    restaurant_id     UUID        NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    name              VARCHAR(60) NOT NULL,
    -- SHA-256 of the recording device's token; the raw token is shown to the owner once.
    device_token_hash VARCHAR(64),
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_kitchen_stations_restaurant_name UNIQUE (restaurant_id, name),
    CONSTRAINT uq_kitchen_stations_device_token_hash UNIQUE (device_token_hash),
    CONSTRAINT uq_kitchen_stations_id_restaurant UNIQUE (id, restaurant_id)
);

CREATE TABLE chefs (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id),
    restaurant_id UUID         NOT NULL REFERENCES restaurants (id),
    display_name  VARCHAR(80)  NOT NULL,
    photo_key     VARCHAR(300),
    bio           VARCHAR(500),
    specialty     VARCHAR(80),
    created_at    TIMESTAMPTZ  NOT NULL,
    removed_at    TIMESTAMPTZ,
    CONSTRAINT uq_chefs_user_id UNIQUE (user_id),
    CONSTRAINT uq_chefs_id_restaurant UNIQUE (id, restaurant_id)
);

CREATE INDEX idx_chefs_restaurant_id ON chefs (restaurant_id);

-- Which chefs currently work a station. Composite FKs keep station and chef in one restaurant.
CREATE TABLE station_assignments (
    station_id    UUID        NOT NULL,
    chef_id       UUID        NOT NULL,
    restaurant_id UUID        NOT NULL,
    assigned_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (station_id, chef_id),
    CONSTRAINT fk_station_assignments_station FOREIGN KEY (station_id, restaurant_id)
        REFERENCES kitchen_stations (id, restaurant_id) ON DELETE CASCADE,
    CONSTRAINT fk_station_assignments_chef FOREIGN KEY (chef_id, restaurant_id)
        REFERENCES chefs (id, restaurant_id)
);

CREATE INDEX idx_station_assignments_chef_id ON station_assignments (chef_id);

-- Append-only consent history. A chef has valid consent when a row for the current version
-- exists with withdrawn_at IS NULL.
CREATE TABLE chef_consents (
    id                UUID        PRIMARY KEY,
    chef_id           UUID        NOT NULL REFERENCES chefs (id),
    consent_version   VARCHAR(20) NOT NULL,
    consent_text_hash VARCHAR(64) NOT NULL,
    accepted_at       TIMESTAMPTZ NOT NULL,
    ip                VARCHAR(45) NOT NULL,
    withdrawn_at      TIMESTAMPTZ
);

CREATE INDEX idx_chef_consents_chef_id ON chef_consents (chef_id);
