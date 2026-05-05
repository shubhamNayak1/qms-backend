-- V24: CAPA module — full rebuild per Kedar-sir flow chart.
--
-- Workflow change (in code, not SQL):
--
--   DRAFT
--    → PENDING_HOD              (Proposed CAPA — HOD: Initial Remedial + Preventive Action)
--    → PENDING_QA_REVIEW (1)    (QA invites depts)
--      ↔ PENDING_DEPT_COMMENT   (cross-functional fan-out, loops back)
--    → PENDING_QA_REVIEW (2)    (QA sets site_head_required)
--    → [PENDING_SITE_HEAD]
--    → PENDING_HEAD_QA          (Head QA approves / rejects)
--    → PENDING_ATTACHMENTS      (each dept uploads + Head QA approves each row)
--    → PENDING_VERIFICATION     (originating dept HOD: Action Taken + Effective Document)
--    → PENDING_VERIFICATION_REVIEW (QA Reviewer accepts / rejects the verification)
--    → CLOSED                   (Head QA: sets effectiveness-assessment frequency + count)
--
-- Post-closure effectiveness-assessment lifecycle (CAPA-only):
--    CLOSED → EFFECTIVENESS_PENDING ↔ EFFECTIVENESS_REVIEW (one cycle per scheduled assessment)
--          → EFFECTIVENESS_VERIFIED (terminal — every cycle accepted)
--
-- New columns on qms_capa:
--
--   parent_record_type        — INCIDENT / DEVIATION / CHANGE_CONTROL /
--                                MARKET_COMPLAINT — the parent module this
--                                CAPA was raised from. NULL when capa_origin
--                                = NEW.
--   parent_record_id          — id of the parent record.
--   parent_record_number      — denormalised parent record number for the
--                                cross-link UI (avoids a polymorphic join).
--   capa_origin               — NEW or EXISTING (training-doc vocabulary).
--   site_head_required        — flag set at the 2nd PENDING_QA_REVIEW pass.
--   verification_review_comment — captured at PENDING_VERIFICATION_REVIEW
--                                  by the QA Reviewer; final accept/reject
--                                  narrative on the verification.
--   assessment_frequency      — set at CLOSED by Head QA.
--                                MONTHLY / QUARTERLY / SEMI_ANNUAL / ANNUAL
--   assessment_count          — number of scheduled effectiveness checks
--                                (e.g. 4 quarterly = annual coverage).
--   assessment_summary_status — denormalised summary of qms_capa_assessments:
--                                NOT_REQUIRED / IN_PROGRESS / COMPLETE.
--                                Computed/maintained by service code; cached
--                                here for indexed list queries.

ALTER TABLE qms_capa
    ADD COLUMN IF NOT EXISTS parent_record_type          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS parent_record_id            BIGINT,
    ADD COLUMN IF NOT EXISTS parent_record_number        VARCHAR(30),
    ADD COLUMN IF NOT EXISTS capa_origin                 VARCHAR(20),
    ADD COLUMN IF NOT EXISTS site_head_required          BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS verification_review_comment TEXT,
    ADD COLUMN IF NOT EXISTS assessment_frequency        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS assessment_count            INT,
    ADD COLUMN IF NOT EXISTS assessment_summary_status   VARCHAR(20) DEFAULT 'NOT_REQUIRED';

-- Backfill: legacy rows that were created with linked_deviation_number set
-- get a polymorphic-parent reading of their existing legacy field. We can't
-- look up the Deviation id by number from SQL here without a join, so leave
-- parent_record_id null for legacy rows — the UI will fall back to the
-- legacy linked_deviation_number text.
UPDATE qms_capa
   SET capa_origin = CASE
       WHEN linked_deviation_number IS NOT NULL AND linked_deviation_number <> ''
            THEN 'EXISTING'
       ELSE 'NEW'
   END
 WHERE capa_origin IS NULL;

UPDATE qms_capa
   SET parent_record_type = 'DEVIATION',
       parent_record_number = linked_deviation_number
 WHERE parent_record_type IS NULL
   AND linked_deviation_number IS NOT NULL
   AND linked_deviation_number <> '';

CREATE INDEX IF NOT EXISTS idx_capa_parent
    ON qms_capa (parent_record_type, parent_record_id)
 WHERE parent_record_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_capa_origin
    ON qms_capa (capa_origin);

CREATE INDEX IF NOT EXISTS idx_capa_assessment_status
    ON qms_capa (assessment_summary_status);

COMMENT ON COLUMN qms_capa.parent_record_type          IS 'Polymorphic parent: INCIDENT / DEVIATION / CHANGE_CONTROL / MARKET_COMPLAINT. NULL when capa_origin = NEW';
COMMENT ON COLUMN qms_capa.parent_record_id            IS 'Polymorphic parent record id';
COMMENT ON COLUMN qms_capa.parent_record_number        IS 'Denormalised parent record number for cross-link UI';
COMMENT ON COLUMN qms_capa.capa_origin                 IS 'NEW or EXISTING — set at create time';
COMMENT ON COLUMN qms_capa.site_head_required          IS 'Set at 2nd PENDING_QA_REVIEW pass — routes through PENDING_SITE_HEAD when true';
COMMENT ON COLUMN qms_capa.verification_review_comment IS 'QA Reviewer narrative captured at PENDING_VERIFICATION_REVIEW';
COMMENT ON COLUMN qms_capa.assessment_frequency        IS 'Effectiveness-assessment frequency set at CLOSED: MONTHLY / QUARTERLY / SEMI_ANNUAL / ANNUAL';
COMMENT ON COLUMN qms_capa.assessment_count            IS 'Number of scheduled effectiveness assessments';
COMMENT ON COLUMN qms_capa.assessment_summary_status   IS 'NOT_REQUIRED / IN_PROGRESS / COMPLETE — denormalised view of qms_capa_assessments rows';

-- ─────────────────────────────────────────────────────────────────
-- New post-closure entity: scheduled CAPA effectiveness-assessment cycles.
-- One row per scheduled cycle (count = qms_capa.assessment_count).
-- ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS qms_capa_assessments (
    id              BIGSERIAL PRIMARY KEY,
    capa_id         BIGINT       NOT NULL,
    sequence_no     INT          NOT NULL,           -- 1, 2, 3, ...
    due_date        DATE,                            -- scheduled date
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                                                     -- PENDING / SUBMITTED / ACCEPTED / REJECTED
    action_observed TEXT,                            -- responsible dept fills
    evidence_ref    VARCHAR(255),                    -- DMS attachment id
    is_effective    BOOLEAN,                         -- the dept's verdict on this cycle
    completed_by_id BIGINT,
    completed_by_name VARCHAR(150),
    completed_at    TIMESTAMP,
    review_status   VARCHAR(20),                     -- ACCEPTED / REJECTED / null until reviewed
    review_comment  TEXT,
    reviewed_by_id  BIGINT,
    reviewed_by_name VARCHAR(150),
    reviewed_at     TIMESTAMP,

    -- BaseEntity fields
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_capa_assess_capa
    ON qms_capa_assessments (capa_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_capa_assess_status
    ON qms_capa_assessments (capa_id, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_capa_assess_due
    ON qms_capa_assessments (due_date, status)
 WHERE is_deleted = FALSE;

COMMENT ON TABLE qms_capa_assessments IS 'Per-cycle effectiveness assessment rows for a closed CAPA. One row per scheduled cycle, count and dates seeded at CAPA closure.';
