-- Admin module Phase 1.3 (2026-10-09) — TCD (Target Closure Days) policy.
--
-- One row per QMS module. Carries the maximum days from Initiate to
-- Close, the warning-window in days, and whether target-date
-- extensions are allowed (plus the hard cap on extensions per record).
--
-- Phase 1 just stores the config and exposes GET/PUT endpoints. The
-- QMS workflow services still enforce the current hard-coded limits
-- (CAPA=30, MC=45, …); Phase 2 wires each service to read from this
-- table so compliance can tune due dates without a code deploy.

CREATE TABLE IF NOT EXISTS tcd_policies (
    id                 BIGSERIAL PRIMARY KEY,
    module_key         VARCHAR(40)  NOT NULL,
    max_days           INT          NOT NULL,
    warning_days       INT          NOT NULL DEFAULT 5,
    extension_allowed  BOOLEAN      NOT NULL DEFAULT TRUE,
    max_extensions     INT          NOT NULL DEFAULT 1,
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT uq_tcd_module UNIQUE (module_key),
    CONSTRAINT ck_tcd_max_days_positive     CHECK (max_days > 0),
    CONSTRAINT ck_tcd_warning_days_nonneg   CHECK (warning_days >= 0),
    CONSTRAINT ck_tcd_max_extensions_nonneg CHECK (max_extensions >= 0)
);

CREATE INDEX IF NOT EXISTS idx_tcd_policy_module ON tcd_policies (module_key)
    WHERE is_deleted = FALSE;

-- Seed defaults — match the limits currently hard-coded in each
-- QMS workflow service. Keeps Phase 1 a strict refactor: nothing
-- about the actual due-date behaviour changes.
INSERT INTO tcd_policies (module_key, max_days, warning_days, extension_allowed, max_extensions, created_by)
VALUES
    ('CAPA',             30, 5, TRUE, 1, 'SYSTEM'),
    ('DEVIATION',        60, 7, TRUE, 1, 'SYSTEM'),
    ('INCIDENT',         30, 5, TRUE, 1, 'SYSTEM'),
    ('MARKET_COMPLAINT', 45, 7, TRUE, 1, 'SYSTEM'),
    ('CHANGE_CONTROL',   90, 10, TRUE, 2, 'SYSTEM')
ON CONFLICT (module_key) DO NOTHING;
