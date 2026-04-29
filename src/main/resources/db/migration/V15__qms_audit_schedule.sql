-- =============================================================
-- V15__qms_audit_schedule.sql
-- QMS Audit Schedule — internal, external, supplier, regulatory
-- =============================================================

CREATE TABLE IF NOT EXISTS qms_audit_schedule (
    id              BIGSERIAL PRIMARY KEY,
    audit_number    VARCHAR(30)  NOT NULL UNIQUE,   -- AUD-202504-0001

    title           VARCHAR(255) NOT NULL,
    audit_type      VARCHAR(30)  NOT NULL,           -- INTERNAL | EXTERNAL | SUPPLIER | REGULATORY
    scope           TEXT,

    -- Lead auditor (denormalised name + optional FK)
    lead_auditor_id   BIGINT REFERENCES users(id) ON DELETE SET NULL,
    lead_auditor_name VARCHAR(150),

    -- Scheduling
    scheduled_date  DATE,
    completed_date  DATE,

    -- Findings & observations (rich text allowed)
    findings        TEXT,
    observations    TEXT,

    -- Workflow
    status          VARCHAR(30) NOT NULL DEFAULT 'PLANNED',  -- PLANNED | IN_PROGRESS | COMPLETED | CANCELLED

    -- Soft delete & audit trail
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
);

CREATE INDEX idx_qms_audit_type     ON qms_audit_schedule(audit_type);
CREATE INDEX idx_qms_audit_status   ON qms_audit_schedule(status);
CREATE INDEX idx_qms_audit_sched    ON qms_audit_schedule(scheduled_date);
CREATE INDEX idx_qms_audit_lead     ON qms_audit_schedule(lead_auditor_id);
CREATE INDEX idx_qms_audit_deleted  ON qms_audit_schedule(is_deleted);
