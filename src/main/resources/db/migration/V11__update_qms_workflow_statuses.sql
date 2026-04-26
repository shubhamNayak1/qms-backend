-- =============================================================
-- V11__update_qms_workflow_statuses.sql
--
-- Migrates all 5 QMS tables to the new per-module granular status
-- model introduced in the workflow redesign.
--
-- Old generic statuses → New statuses:
--   OPEN             → DRAFT           (not yet reviewed)
--   IN_PROGRESS      → PENDING_HOD     (under department review — closest match)
--   PENDING_APPROVAL → PENDING_HEAD_QA (awaiting final approval)
--   APPROVED         → PENDING_VERIFICATION (post-approval, not yet closed)
--   REJECTED         → REJECTED        (unchanged)
--   CLOSED           → CLOSED          (unchanged)
--   CANCELLED        → CANCELLED       (unchanged)
--   REOPENED         → DRAFT           (reset to draft for rework)
--
-- Also adds new columns required by the updated entity model:
--   qms_incident:       incident_sub_type, retesting_required, deviation_required
--   qms_change_control: site_head_required, customer_comment_required, customer_comment
-- =============================================================

-- ─── Update default column values ─────────────────────────────
ALTER TABLE qms_capa            ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE qms_deviation       ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE qms_incident        ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE qms_change_control  ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE qms_market_complaint ALTER COLUMN status SET DEFAULT 'DRAFT';

-- ─── Migrate existing status values in all 5 tables ───────────

-- qms_capa
UPDATE qms_capa SET status = 'DRAFT'            WHERE status = 'OPEN';
UPDATE qms_capa SET status = 'PENDING_HOD'      WHERE status = 'IN_PROGRESS';
UPDATE qms_capa SET status = 'PENDING_HEAD_QA'  WHERE status = 'PENDING_APPROVAL';
UPDATE qms_capa SET status = 'PENDING_VERIFICATION' WHERE status = 'APPROVED';
UPDATE qms_capa SET status = 'DRAFT'            WHERE status = 'REOPENED';

-- qms_deviation
UPDATE qms_deviation SET status = 'DRAFT'            WHERE status = 'OPEN';
UPDATE qms_deviation SET status = 'PENDING_HOD'      WHERE status = 'IN_PROGRESS';
UPDATE qms_deviation SET status = 'PENDING_HEAD_QA'  WHERE status = 'PENDING_APPROVAL';
UPDATE qms_deviation SET status = 'PENDING_VERIFICATION' WHERE status = 'APPROVED';
UPDATE qms_deviation SET status = 'DRAFT'            WHERE status = 'REOPENED';

-- qms_incident
UPDATE qms_incident SET status = 'DRAFT'            WHERE status = 'OPEN';
UPDATE qms_incident SET status = 'PENDING_HOD'      WHERE status = 'IN_PROGRESS';
UPDATE qms_incident SET status = 'PENDING_HEAD_QA'  WHERE status = 'PENDING_APPROVAL';
UPDATE qms_incident SET status = 'PENDING_VERIFICATION' WHERE status = 'APPROVED';
UPDATE qms_incident SET status = 'DRAFT'            WHERE status = 'REOPENED';

-- qms_change_control
UPDATE qms_change_control SET status = 'DRAFT'            WHERE status = 'OPEN';
UPDATE qms_change_control SET status = 'PENDING_HOD'      WHERE status = 'IN_PROGRESS';
UPDATE qms_change_control SET status = 'PENDING_HEAD_QA'  WHERE status = 'PENDING_APPROVAL';
UPDATE qms_change_control SET status = 'PENDING_VERIFICATION' WHERE status = 'APPROVED';
UPDATE qms_change_control SET status = 'DRAFT'            WHERE status = 'REOPENED';

-- qms_market_complaint
UPDATE qms_market_complaint SET status = 'DRAFT'            WHERE status = 'OPEN';
UPDATE qms_market_complaint SET status = 'PENDING_HOD'      WHERE status = 'IN_PROGRESS';
UPDATE qms_market_complaint SET status = 'PENDING_HEAD_QA'  WHERE status = 'PENDING_APPROVAL';
UPDATE qms_market_complaint SET status = 'PENDING_VERIFICATION' WHERE status = 'APPROVED';
UPDATE qms_market_complaint SET status = 'DRAFT'            WHERE status = 'REOPENED';

-- ─── New columns: Incident ─────────────────────────────────────
ALTER TABLE qms_incident
    ADD COLUMN IF NOT EXISTS incident_sub_type  VARCHAR(20)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS retesting_required BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deviation_required BOOLEAN      NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN qms_incident.incident_sub_type
    IS 'LABORATORY (OOS/OOT) or GENERAL — routes investigation through different steps';
COMMENT ON COLUMN qms_incident.retesting_required
    IS 'TRUE if lab retesting is needed — routes through PENDING_ATTACHMENTS';
COMMENT ON COLUMN qms_incident.deviation_required
    IS 'TRUE if a Deviation record must also be raised for this incident';

-- ─── New columns: Change Control ──────────────────────────────
ALTER TABLE qms_change_control
    ADD COLUMN IF NOT EXISTS site_head_required         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS customer_comment_required  BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS customer_comment           TEXT         DEFAULT NULL;

COMMENT ON COLUMN qms_change_control.site_head_required
    IS 'TRUE if this change requires Site Head approval — routes through PENDING_SITE_HEAD';
COMMENT ON COLUMN qms_change_control.customer_comment_required
    IS 'TRUE if customer notification is required — routes through PENDING_CUSTOMER_COMMENT';
COMMENT ON COLUMN qms_change_control.customer_comment
    IS 'Customer comment text filled during PENDING_CUSTOMER_COMMENT stage';
