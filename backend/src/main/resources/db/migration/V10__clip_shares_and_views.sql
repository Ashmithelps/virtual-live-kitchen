-- Public share links for clips. The token is stored as-is (like table QR tokens) so the customer can copy their
-- link again later; revoking sets revoked_at. At most one active link per clip.
CREATE TABLE clip_shares (
    id          UUID        PRIMARY KEY,
    clip_id     UUID        NOT NULL REFERENCES clips (id) ON DELETE CASCADE,
    token       VARCHAR(64) NOT NULL UNIQUE,
    created_by  UUID        NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_clip_shares_active ON clip_shares (clip_id) WHERE revoked_at IS NULL;

-- One row per (clip, visitor, IST day): views are deduplicated per visitor per day. The visitor id comes from the
-- browser and is stored only as a SHA-256 hash.
CREATE TABLE clip_views (
    clip_id       UUID        NOT NULL REFERENCES clips (id) ON DELETE CASCADE,
    view_date     DATE        NOT NULL,
    visitor_hash  VARCHAR(64) NOT NULL,
    share_id      UUID        REFERENCES clip_shares (id) ON DELETE SET NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (clip_id, view_date, visitor_hash)
);
