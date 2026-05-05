-- ============================================================
-- VaultCache Database Schema
-- V1 — rate_limit_configs, usage_events, users
-- ============================================================

CREATE TABLE IF NOT EXISTS rate_limit_configs (
    id                    BIGSERIAL    PRIMARY KEY,
    subject               VARCHAR(255) NOT NULL,
    key_type              VARCHAR(20)  NOT NULL CHECK (key_type IN ('API_KEY','IP','USER')),
    algorithm             VARCHAR(30)  NOT NULL CHECK (algorithm IN ('FIXED_WINDOW','SLIDING_WINDOW','TOKEN_BUCKET')),
    rate_limit            INTEGER      NOT NULL CHECK (rate_limit > 0),
    window_seconds        INTEGER      NOT NULL CHECK (window_seconds > 0),
    refill_rate_per_second INTEGER     NOT NULL DEFAULT 10,
    tier                  VARCHAR(30),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    blocked               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_rlc_subject  ON rate_limit_configs (subject);
CREATE INDEX idx_rlc_key_type ON rate_limit_configs (key_type);
CREATE INDEX idx_rlc_active   ON rate_limit_configs (active);

-- ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS usage_events (
    id               BIGSERIAL    PRIMARY KEY,
    subject          VARCHAR(255) NOT NULL,
    allowed          BOOLEAN      NOT NULL,
    algorithm        VARCHAR(30),
    redis_latency_ms BIGINT       DEFAULT 0,
    endpoint         VARCHAR(255),
    occurred_at      TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_ue_subject_time ON usage_events (subject, occurred_at DESC);
CREATE INDEX idx_ue_occurred_at  ON usage_events (occurred_at DESC);
CREATE INDEX idx_ue_allowed      ON usage_events (allowed);

-- ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ  DEFAULT NOW(),
    CONSTRAINT chk_role CHECK (role IN ('ADMIN','USER'))
);

-- ── Seed default rate limit configs ──────────────────────────
INSERT INTO rate_limit_configs
    (subject, key_type, algorithm, rate_limit, window_seconds, refill_rate_per_second, tier)
VALUES
    ('free-tier',       'API_KEY', 'SLIDING_WINDOW', 60,   60,  1,  'FREE'),
    ('pro-tier',        'API_KEY', 'SLIDING_WINDOW', 1000, 60,  10, 'PRO'),
    ('enterprise-tier', 'API_KEY', 'TOKEN_BUCKET',   5000, 60,  50, 'ENTERPRISE'),
    ('blocked-example', 'API_KEY', 'FIXED_WINDOW',   0,    60,  1,  'BLOCKED');

UPDATE rate_limit_configs SET blocked = TRUE WHERE subject = 'blocked-example';
