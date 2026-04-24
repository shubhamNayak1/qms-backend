-- =============================================================
-- V10__password_policy_and_history.sql
-- Adds:
--   1. must_change_password flag on users
--   2. password_policies table (admin-managed policy)
--   3. password_histories table (per-user history for reuse prevention)
-- =============================================================

-- ─── users: first-login / force-change flag ───────────────────
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- ─── password_policies ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS password_policies (
    id                              BIGSERIAL PRIMARY KEY,
    password_length_min             INT       NOT NULL DEFAULT 8,
    password_length_max             INT       NOT NULL DEFAULT 128,
    alpha_min                       INT       NOT NULL DEFAULT 1,
    numeric_min                     INT       NOT NULL DEFAULT 1,
    special_char_min                INT       NOT NULL DEFAULT 1,
    upper_case_min                  INT       NOT NULL DEFAULT 1,
    number_of_login_attempts        INT       NOT NULL DEFAULT 5,
    valid_period                    INT       NOT NULL DEFAULT 90,
    previous_password_attempt_track INT       NOT NULL DEFAULT 5,
    effective_date                  DATE      NOT NULL,
    is_deleted                      BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at                      TIMESTAMP NOT NULL,
    updated_at                      TIMESTAMP,
    created_by                      VARCHAR(100),
    updated_by                      VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_pwd_policy_effective ON password_policies (effective_date DESC)
    WHERE is_deleted = FALSE;

-- ─── password_histories ──────────────────────────────────────
-- Stores BCrypt hashes of previous passwords so that
-- "previousPasswordAttemptTrack" reuse prevention can be enforced.
CREATE TABLE IF NOT EXISTS password_histories (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ph_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pwd_history_user_time
    ON password_histories (user_id, created_at DESC);

-- ─── Seed a default permissive policy ───────────────────────
-- Effective immediately so fresh installations have a policy.
INSERT INTO password_policies (
    password_length_min, password_length_max,
    alpha_min, numeric_min, special_char_min, upper_case_min,
    number_of_login_attempts, valid_period,
    previous_password_attempt_track,
    effective_date, is_deleted, created_at, created_by
) VALUES (
    8, 128,
    1, 1, 1, 1,
    5, 90,
    5,
    CURRENT_DATE, FALSE, NOW(), 'SYSTEM'
);
