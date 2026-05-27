-- V25: Change Control tester-feedback phase 1
--
-- Items 1-23 from the May 2026 tester walkthrough.
--
-- ── Change Control entity additions ──────────────────────────────
-- product_material_code   — Material/Product Code captured at Create
--                            (split from the legacy productMaterial Name field).
-- pre_remark              — QA's pre-dept-comment narrative captured at
--                            QA Evaluation Phase 1. Visible to every invited
--                            dept while they fill their comments.
-- initial_attachment_ref  — DMS document id (or free-text reference)
--                            captured at Create time. Resolves to the DMS
--                            title/version on the response (same pattern
--                            as the dept-attachment table).
-- resend_count            — How many times the record has been Resent to
--                            Initiator from HOD Assessment. Lets the QA
--                            timeline render "resent N times" so the audit
--                            trail is glanceable.
--
-- ── qms_department_comments extensions ───────────────────────────
-- action_required — Set by commenting dept HOD when their feedback says a
--                   follow-up action is needed.
-- target_date     — Required when action_required = TRUE. The application
--                   layer enforces target_date ≤ parent record's
--                   target_completion_date.

ALTER TABLE qms_change_control
    ADD COLUMN IF NOT EXISTS product_material_code  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pre_remark             TEXT,
    ADD COLUMN IF NOT EXISTS initial_attachment_ref VARCHAR(255),
    ADD COLUMN IF NOT EXISTS resend_count           INT NOT NULL DEFAULT 0;

ALTER TABLE qms_department_comments
    ADD COLUMN IF NOT EXISTS action_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS target_date     DATE;

COMMENT ON COLUMN qms_change_control.product_material_code  IS 'Material/Product Code captured at Create (split from productMaterial Name)';
COMMENT ON COLUMN qms_change_control.pre_remark             IS 'Pre-Remark from QA at Phase 1 — visible to all invited dept HODs during their dept-comment fan-out';
COMMENT ON COLUMN qms_change_control.initial_attachment_ref IS 'DMS document id (or free text) captured at Create. Same resolution pattern as qms_department_attachments.attachment_ref';
COMMENT ON COLUMN qms_change_control.resend_count           IS 'Counter — incremented each time HOD Resends the record back to Initiator (PENDING_HOD → DRAFT)';
COMMENT ON COLUMN qms_department_comments.action_required   IS 'TRUE when the dept''s comment requires a follow-up action — target_date becomes mandatory';
COMMENT ON COLUMN qms_department_comments.target_date       IS 'Required when action_required = TRUE; the application enforces target_date ≤ parent record''s target_completion_date';

-- ── Also propagate resend_count to the other 4 modules so the same
--    Resend-to-Initiator transition works uniformly. Same column on
--    every parent table avoids module-specific service code paths.

ALTER TABLE qms_capa             ADD COLUMN IF NOT EXISTS resend_count INT NOT NULL DEFAULT 0;
ALTER TABLE qms_deviation        ADD COLUMN IF NOT EXISTS resend_count INT NOT NULL DEFAULT 0;
ALTER TABLE qms_incident         ADD COLUMN IF NOT EXISTS resend_count INT NOT NULL DEFAULT 0;
ALTER TABLE qms_market_complaint ADD COLUMN IF NOT EXISTS resend_count INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN qms_capa.resend_count             IS 'Counter — incremented each time HOD Resends the record back to Initiator';
COMMENT ON COLUMN qms_deviation.resend_count        IS 'Counter — incremented each time HOD Resends the record back to Initiator';
COMMENT ON COLUMN qms_incident.resend_count         IS 'Counter — incremented each time HOD Resends the record back to Initiator';
COMMENT ON COLUMN qms_market_complaint.resend_count IS 'Counter — incremented each time HOD Resends the record back to Initiator';
