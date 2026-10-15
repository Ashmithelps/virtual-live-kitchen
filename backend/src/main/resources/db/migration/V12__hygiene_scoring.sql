-- AI hygiene analysis of READY clips. One row per (clip, model version); the newest row is the clip's result.
CREATE TABLE hygiene_results (
    id                 UUID          PRIMARY KEY,
    clip_id            UUID          NOT NULL REFERENCES clips (id) ON DELETE CASCADE,
    restaurant_id      UUID          NOT NULL REFERENCES restaurants (id),
    chef_id            UUID          REFERENCES chefs (id),
    model_version      VARCHAR(120)  NOT NULL,
    sampled_frames     INTEGER       NOT NULL,
    person_frames      INTEGER       NOT NULL,
    -- Per item: % of judged person-frames compliant; NULL when the item couldn't be judged.
    gloves_pct         NUMERIC(5,1),
    head_cover_pct     NUMERIC(5,1),
    mask_pct           NUMERIC(5,1),
    -- Mean of the item percentages that could be judged (0–100); NULL = nothing judged (not counted in averages).
    score              NUMERIC(5,1),
    -- {"gloves": [12.0, 20.0, 31.0], "headCover": [...], "mask": [...]} seconds into the clip
    violations         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    -- Admin decisions after a dispute: a corrected score, or the result left out of all averages.
    override_score     NUMERIC(5,1),
    excluded           BOOLEAN       NOT NULL DEFAULT FALSE,
    override_note      VARCHAR(500),
    overridden_by      UUID          REFERENCES users (id),
    overridden_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_hygiene_result_clip_model UNIQUE (clip_id, model_version),
    CONSTRAINT ck_hygiene_pcts CHECK ((gloves_pct IS NULL OR gloves_pct BETWEEN 0 AND 100)
        AND (head_cover_pct IS NULL OR head_cover_pct BETWEEN 0 AND 100) AND (mask_pct IS NULL OR mask_pct BETWEEN 0 AND 100)
        AND (score IS NULL OR score BETWEEN 0 AND 100) AND (override_score IS NULL OR override_score BETWEEN 0 AND 100))
);

CREATE INDEX idx_hygiene_results_restaurant ON hygiene_results (restaurant_id, created_at DESC);
CREATE INDEX idx_hygiene_results_chef ON hygiene_results (chef_id, created_at DESC);

-- Owner disputes of a result, reviewed by admins.
CREATE TABLE hygiene_disputes (
    id             UUID          PRIMARY KEY,
    result_id      UUID          NOT NULL REFERENCES hygiene_results (id) ON DELETE CASCADE,
    restaurant_id  UUID          NOT NULL REFERENCES restaurants (id),
    opened_by      UUID          NOT NULL REFERENCES users (id),
    reason         VARCHAR(1000) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    admin_note     VARCHAR(500),
    resolved_by    UUID          REFERENCES users (id),
    resolved_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_hygiene_dispute_status CHECK (status IN ('OPEN', 'UPHELD', 'REJECTED'))
);

CREATE UNIQUE INDEX uq_hygiene_dispute_open ON hygiene_disputes (result_id) WHERE status = 'OPEN';
CREATE INDEX idx_hygiene_disputes_status ON hygiene_disputes (status, created_at);

-- Platform-wide switches set by admins.
CREATE TABLE platform_settings (
    key         VARCHAR(80)   PRIMARY KEY,
    value       VARCHAR(500)  NOT NULL,
    updated_by  UUID          REFERENCES users (id),
    updated_at  TIMESTAMPTZ   NOT NULL
);
