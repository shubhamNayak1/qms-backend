-- =============================================================
-- V5__init_audit_logs.sql (PostgreSQL FIXED)
-- =============================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,

    -- Who
    user_id BIGINT REFERENCES users(id),
    username VARCHAR(100),
    user_full_name VARCHAR(160),
    user_department VARCHAR(100),

    -- What
    action VARCHAR(50) NOT NULL,
    module VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    entity_id BIGINT,
    description VARCHAR(500),

    -- Before / After
    old_value TEXT,
    new_value TEXT,

    -- Outcome
    outcome VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    error_type VARCHAR(200),
    error_message VARCHAR(1000),

    -- Network
    ip_address VARCHAR(60),
    user_agent VARCHAR(500),
    request_uri VARCHAR(300),
    correlation_id VARCHAR(100),
    session_id VARCHAR(100),

    -- Timing
    event_timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    duration_ms BIGINT
);

-- Indexes (Flyway safe)
CREATE INDEX IF NOT EXISTS idx_al_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_al_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_al_module ON audit_logs(module);
CREATE INDEX IF NOT EXISTS idx_al_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_al_timestamp ON audit_logs(event_timestamp);
CREATE INDEX IF NOT EXISTS idx_al_outcome ON audit_logs(outcome);
CREATE INDEX IF NOT EXISTS idx_al_session_id ON audit_logs(session_id);
CREATE INDEX IF NOT EXISTS idx_al_correlation ON audit_logs(correlation_id);

-- Composite indexes
CREATE INDEX IF NOT EXISTS idx_al_module_ts ON audit_logs(module, event_timestamp);
CREATE INDEX IF NOT EXISTS idx_al_user_ts ON audit_logs(user_id, event_timestamp);
CREATE INDEX IF NOT EXISTS idx_al_action_ts ON audit_logs(action, event_timestamp);


-- =============================================================
-- Archive table
-- =============================================================
CREATE TABLE IF NOT EXISTS audit_logs_archive
(LIKE audit_logs INCLUDING ALL);


-- =============================================================
-- Seed data
-- =============================================================
INSERT INTO audit_logs (
    action, module, description, outcome
) VALUES (
    'SYSTEM_EVENT',
    'SYSTEM',
    'QMS Audit Module initialised — schema V5 applied',
    'SUCCESS'
);