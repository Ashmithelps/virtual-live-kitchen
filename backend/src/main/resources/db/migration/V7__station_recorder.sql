-- Milestone 9: station recorder devices and clip requests.

-- Pairing: the owner shows a one-time code as a QR; the station phone exchanges it for a device token.
ALTER TABLE kitchen_stations
    ADD COLUMN pairing_code_hash  VARCHAR(64),
    ADD COLUMN pairing_expires_at TIMESTAMPTZ,
    ADD COLUMN device_paired_at   TIMESTAMPTZ,
    ADD CONSTRAINT uq_kitchen_stations_pairing_code_hash UNIQUE (pairing_code_hash);

-- What the customer can expect: PENDING (footage requested from the station), UPLOADED (raw
-- footage stored, processing comes later), NOT_AVAILABLE (no consent, no device, no footage…).
ALTER TABLE orders
    ADD COLUMN clip_status VARCHAR(20),
    ADD CONSTRAINT ck_orders_clip_status CHECK (clip_status IS NULL OR clip_status IN ('PENDING', 'UPLOADED', 'NOT_AVAILABLE'));

-- One per order that reached READY: which station must upload which time window.
CREATE TABLE clip_requests (
    id            UUID         PRIMARY KEY,
    order_id      UUID         NOT NULL REFERENCES orders (id),
    restaurant_id UUID         NOT NULL REFERENCES restaurants (id),
    station_id    UUID         REFERENCES kitchen_stations (id) ON DELETE SET NULL,
    chef_id       UUID         REFERENCES chefs (id),
    -- server time: PREPARING minus 10 s .. READY plus 5 s
    window_start  TIMESTAMPTZ  NOT NULL,
    window_end    TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    reason        VARCHAR(30),
    -- object keys in the clips bucket under raw/{restaurant_id}/{order_id}/
    chunk_keys    TEXT[]       NOT NULL DEFAULT '{}',
    requested_at  TIMESTAMPTZ  NOT NULL,
    uploaded_at   TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_clip_requests_order UNIQUE (order_id),
    CONSTRAINT ck_clip_requests_window CHECK (window_end > window_start),
    CONSTRAINT ck_clip_requests_status CHECK (status IN ('PENDING_UPLOAD', 'UPLOADED', 'NOT_AVAILABLE')),
    CONSTRAINT ck_clip_requests_reason CHECK (reason IS NULL OR reason IN
        ('NO_CONSENT', 'NO_DEVICE', 'NO_FOOTAGE', 'UPLOAD_TIMEOUT'))
);

CREATE INDEX idx_clip_requests_station_pending ON clip_requests (station_id) WHERE status = 'PENDING_UPLOAD';
