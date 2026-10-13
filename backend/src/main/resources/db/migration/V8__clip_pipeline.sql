-- Milestone 10: job queue, processed clips, retention, restaurant logos.

-- Generic Postgres-backed job queue. Workers claim with FOR UPDATE SKIP LOCKED.
CREATE TABLE jobs (
    id           UUID         PRIMARY KEY,
    type         VARCHAR(40)  NOT NULL,
    payload      JSONB        NOT NULL,
    -- at most one QUEUED/RUNNING job per key (e.g. clip:<orderId>)
    dedupe_key   VARCHAR(100),
    status       VARCHAR(12)  NOT NULL,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    max_attempts INTEGER      NOT NULL,
    run_after    TIMESTAMPTZ  NOT NULL,
    locked_by    VARCHAR(100),
    locked_at    TIMESTAMPTZ,
    last_error   TEXT,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    finished_at  TIMESTAMPTZ,
    CONSTRAINT ck_jobs_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'DEAD')),
    CONSTRAINT ck_jobs_attempts CHECK (attempts >= 0 AND max_attempts > 0)
);

CREATE INDEX idx_jobs_claimable ON jobs (run_after) WHERE status = 'QUEUED';
CREATE INDEX idx_jobs_running ON jobs (locked_at) WHERE status = 'RUNNING';
CREATE UNIQUE INDEX uq_jobs_dedupe_active ON jobs (dedupe_key) WHERE status IN ('QUEUED', 'RUNNING');

-- The customer-facing clip for an order (one per order that reached READY).
CREATE TABLE clips (
    id             UUID          PRIMARY KEY,
    order_id       UUID          NOT NULL REFERENCES orders (id),
    restaurant_id  UUID          NOT NULL REFERENCES restaurants (id),
    chef_id        UUID          REFERENCES chefs (id),
    station_id     UUID          REFERENCES kitchen_stations (id) ON DELETE SET NULL,
    status         VARCHAR(16)   NOT NULL,
    duration_s     NUMERIC(6, 2),
    -- clips bucket: clips/{restaurant_id}/{order_id}.mp4 / .jpg
    video_key      VARCHAR(300),
    thumb_key      VARCHAR(300),
    failure_reason VARCHAR(300),
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    ready_at       TIMESTAMPTZ,
    -- when the files are deleted (retention); status becomes EXPIRED
    expires_at     TIMESTAMPTZ,
    CONSTRAINT uq_clips_order UNIQUE (order_id),
    CONSTRAINT ck_clips_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'NOT_AVAILABLE', 'EXPIRED'))
);

CREATE INDEX idx_clips_expiry ON clips (expires_at) WHERE status = 'READY';

ALTER TABLE clip_requests ADD COLUMN raw_deleted_at TIMESTAMPTZ;
CREATE INDEX idx_clip_requests_raw_cleanup ON clip_requests (requested_at) WHERE raw_deleted_at IS NULL;

ALTER TABLE restaurants ADD COLUMN logo_key VARCHAR(300);

ALTER TABLE orders DROP CONSTRAINT ck_orders_clip_status;
ALTER TABLE orders ADD CONSTRAINT ck_orders_clip_status CHECK (clip_status IS NULL OR clip_status IN
    ('PENDING', 'UPLOADED', 'PROCESSING', 'READY', 'FAILED', 'NOT_AVAILABLE', 'EXPIRED'));

-- Backfill: every existing clip request gets a clip row; footage uploaded in M9 is queued for processing.
INSERT INTO clips (id, order_id, restaurant_id, chef_id, station_id, status, failure_reason, created_at, updated_at)
SELECT gen_random_uuid(), order_id, restaurant_id, chef_id, station_id,
       CASE status WHEN 'NOT_AVAILABLE' THEN 'NOT_AVAILABLE' ELSE 'PENDING' END,
       reason, requested_at, updated_at
FROM clip_requests;

INSERT INTO jobs (id, type, payload, dedupe_key, status, attempts, max_attempts, run_after, created_at, updated_at)
SELECT gen_random_uuid(), 'CLIP_PROCESS', jsonb_build_object('orderId', order_id), 'clip:' || order_id,
       'QUEUED', 0, 5, now(), now(), now()
FROM clip_requests
WHERE status = 'UPLOADED';
