-- V21: Market Complaint module — domain extensions to match the Kedar-sir
-- training spec.
--
-- Workflow change (handled in code, not SQL):
--   DRAFT → PENDING_HOD (review only, no routing)
--          → PENDING_INVESTIGATION (QA's home — adds dept comments here)
--          ↔ PENDING_DEPT_COMMENT (cross-functional fan-out, loops back)
--          → PENDING_HEAD_QA → CLOSED
--
-- New columns on qms_market_complaint:
--
--   complaint_origin        — NEW or EXISTING (links a follow-up MC back to
--                             its parent MC record)
--   parent_complaint_id     — FK to qms_market_complaint.id when origin
--                             = EXISTING
--   complaint_subject       — what the complaint is about (Product / Packing /
--                             Transportation / Labels / Drum / Shipper /
--                             Carton / Bag)
--   capa_required           — boolean set during QA Investigation
--   investigation_findings  — free-text findings captured by QA Reviewer
--   impact_assessment       — free-text impact rating captured by QA Reviewer
--
-- The existing capa_reference column doubles as the "linked CAPA number"
-- when capa_required = true; we keep its name for backwards compat.

ALTER TABLE qms_market_complaint
    ADD COLUMN IF NOT EXISTS complaint_origin       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS parent_complaint_id    BIGINT,
    ADD COLUMN IF NOT EXISTS complaint_subject      VARCHAR(50),
    ADD COLUMN IF NOT EXISTS capa_required          BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS investigation_findings TEXT,
    ADD COLUMN IF NOT EXISTS impact_assessment      TEXT;

-- Default existing rows to NEW so the legacy data has a sensible origin.
UPDATE qms_market_complaint
   SET complaint_origin = 'NEW'
 WHERE complaint_origin IS NULL;

-- FK index on parent_complaint_id so we can render the parent-child tree
-- efficiently in the detail drawer.
CREATE INDEX IF NOT EXISTS idx_mc_parent_id
    ON qms_market_complaint (parent_complaint_id)
 WHERE parent_complaint_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_mc_subject
    ON qms_market_complaint (complaint_subject);

COMMENT ON COLUMN qms_market_complaint.complaint_origin       IS 'NEW or EXISTING (follow-up to a prior complaint)';
COMMENT ON COLUMN qms_market_complaint.parent_complaint_id    IS 'Parent MC id when complaint_origin = EXISTING';
COMMENT ON COLUMN qms_market_complaint.complaint_subject      IS 'Product / Packing / Transportation / Labels / Drum / Shipper / Carton / Bag';
COMMENT ON COLUMN qms_market_complaint.capa_required          IS 'Set by QA Reviewer at PENDING_INVESTIGATION — drives CAPA cross-link';
COMMENT ON COLUMN qms_market_complaint.investigation_findings IS 'Detailed findings captured during QA Investigation';
COMMENT ON COLUMN qms_market_complaint.impact_assessment      IS 'Impact assessment narrative captured during QA Investigation';
