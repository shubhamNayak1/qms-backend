-- V22: Deviation module — domain extensions to match the Kedar-sir flow
-- chart and training spec.
--
-- Workflow change (handled in code, not SQL):
--
--   DRAFT
--    → PENDING_HOD            (HOD Assessment + initial/detailed investigation;
--                              optionally creates a CAPA here — CAPA # is
--                              stamped back via linked_capa_number)
--    → PENDING_QA_REVIEW (1)  (QA invites depts)
--      ↔ PENDING_DEPT_COMMENT (cross-functional fan-out, loops back)
--    → PENDING_QA_REVIEW (2)  (QA re-evaluates; sets site_head_required +
--                              customer_comment_required)
--    → PENDING_RA_REVIEW + (optional) PENDING_CUSTOMER_COMMENT (parallel)
--    → (optional) PENDING_SITE_HEAD
--    → PENDING_HEAD_QA        (Head QA approve / reject)
--    → PENDING_ATTACHMENTS    (each responsible dept uploads + Head QA
--                              approves their attachment row — closure
--                              gated until ALL approved)
--    → PENDING_VERIFICATION   (originating-dept HOD adds Investigation
--                              Summary)
--    → CLOSED
--
-- New columns on qms_deviation:
--
--   parent_incident_id   — FK back to the parent Incident the Deviation
--                          was raised against. Per Kedar sir, every
--                          Deviation must originate from an Incident
--                          where deviation_required = TRUE.
--   linked_capa_number   — CAPA record number stamped at HOD Assessment
--                          when CAPA Required = Yes. Mirrors
--                          ChangeControl.linked_capa_number / capa_reference
--                          but kept as a distinct column so the audit
--                          diff is unambiguous.
--   site_head_required   — flag set by QA Reviewer (2nd evaluation)
--                          driving the optional PENDING_SITE_HEAD branch.
--   customer_comment_req — flag set by QA Reviewer (2nd evaluation)
--                          driving the optional PENDING_CUSTOMER_COMMENT
--                          branch (parallel with RA).
--   investigation_summary — text written at PENDING_VERIFICATION by the
--                          originating dept HOD; appears on the printed
--                          closure cover sheet.
--
-- The existing capa_required boolean stays — flips on at HOD Assessment
-- and triggers the CAPA cross-link.

ALTER TABLE qms_deviation
    ADD COLUMN IF NOT EXISTS parent_incident_id      BIGINT,
    ADD COLUMN IF NOT EXISTS linked_capa_number      VARCHAR(30),
    ADD COLUMN IF NOT EXISTS site_head_required      BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS customer_comment_required BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS investigation_summary   TEXT;

CREATE INDEX IF NOT EXISTS idx_dev_parent_incident
    ON qms_deviation (parent_incident_id)
 WHERE parent_incident_id IS NOT NULL;

COMMENT ON COLUMN qms_deviation.parent_incident_id        IS 'Parent Incident id this Deviation was raised against (Incidents flag deviation_required = TRUE)';
COMMENT ON COLUMN qms_deviation.linked_capa_number        IS 'CAPA record number generated at HOD Assessment when CAPA Required = TRUE';
COMMENT ON COLUMN qms_deviation.site_head_required        IS 'Set at PENDING_QA_REVIEW (2nd pass); routes through PENDING_SITE_HEAD when true';
COMMENT ON COLUMN qms_deviation.customer_comment_required IS 'Set at PENDING_QA_REVIEW (2nd pass); routes through PENDING_CUSTOMER_COMMENT when true';
COMMENT ON COLUMN qms_deviation.investigation_summary     IS 'Closure cover-sheet narrative captured at PENDING_VERIFICATION by originating dept HOD';

-- ─────────────────────────────────────────────────────────────────
-- New cross-cutting table: department attachment-approval rows.
--
-- Lives at the QmsRecord level (record_type + record_id polymorphic FK)
-- so we can reuse this pattern for Change Control / Market Complaint
-- later. For Deviation it backs the PENDING_ATTACHMENTS stage:
--   • QA / Head QA invites a department to upload its attachment.
--   • The dept uploads via the existing DMS flow and pastes the
--     reference into this row.
--   • Head QA flips the row from PENDING → APPROVED (or REJECTED with
--     a comment). All rows must be APPROVED before closure unlocks.
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS qms_department_attachments (
    id              BIGSERIAL PRIMARY KEY,
    record_type     VARCHAR(30) NOT NULL,
    record_id       BIGINT      NOT NULL,
    department_id   BIGINT      NOT NULL,
    department_name VARCHAR(150),
    attachment_ref  VARCHAR(255),
    attachment_note TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
    decided_by_id   BIGINT,
    decided_by_name VARCHAR(150),
    decided_at      TIMESTAMP,
    decision_note   TEXT,

    -- BaseEntity fields
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_dept_attach_record
    ON qms_department_attachments (record_type, record_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_dept_attach_status
    ON qms_department_attachments (record_type, record_id, status, is_deleted);

COMMENT ON TABLE  qms_department_attachments IS 'Per-department attachment-approval rows backing the PENDING_ATTACHMENTS gate. Polymorphic on (record_type, record_id) so the same table serves Deviation, Change Control, Market Complaint.';
COMMENT ON COLUMN qms_department_attachments.status IS 'PENDING when invited, APPROVED / REJECTED once Head QA decides';
