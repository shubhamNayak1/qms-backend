-- V23: Incident module — domain extensions to match the Kedar-sir flow chart.
--
-- The Incident lifecycle now has FOUR end-to-end paths (driven by the HOD's
-- branching flags after assessment):
--
--   Lab + Retesting Required  → CAPA(opt) → QA Review → [Site Head] → Head QA → Attachments → Verification → Closed
--   Lab + No Retesting        → "Abnormality in Proposed RA" → Head QA → Attachments → Verification → Closed
--   General + No Deviation    → CAPA(opt) → QA Review (1) → Dept Comments → QA Review (2) → [Site Head] → Head QA → Attachments → Verification → Closed
--   General + Deviation Req.  → QA Review → Deviation # generated → [DEVIATION_SPAWNED — Incident terminates here]
--
-- Workflow change is in code (WorkflowTransition.INCIDENT_T). This migration
-- adds the columns each path needs:
--
--   site_head_required        — set by QA Reviewer at Assessment by QA;
--                                routes through PENDING_SITE_HEAD when true.
--   capa_required             — set by HOD at Assessment; drives CAPA cross-link.
--                                The existing capa_reference column captures
--                                the linked CAPA #.
--   linked_capa_number        — explicit "CAPA generated at HOD assessment"
--                                column distinct from capa_reference so the
--                                audit diff makes the lifecycle moment clear.
--   abnormality_remedial_action — narrative captured by QA Reviewer on the
--                                Lab + No-Retest path (the chart's
--                                "Abnormality in Proposed RA" stage).
--   spawned_deviation_id      — cross-link to the Deviation that this
--                                Incident spawned (when General +
--                                deviation_required = TRUE).
--   spawned_deviation_number  — denormalised record number for the same
--                                cross-link (display without a join).
--   verification_narrative    — Verification stage narrative (originating
--                                dept HOD writes it).

ALTER TABLE qms_incident
    ADD COLUMN IF NOT EXISTS site_head_required          BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS capa_required               BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS linked_capa_number          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS abnormality_remedial_action TEXT,
    ADD COLUMN IF NOT EXISTS spawned_deviation_id        BIGINT,
    ADD COLUMN IF NOT EXISTS spawned_deviation_number    VARCHAR(30),
    ADD COLUMN IF NOT EXISTS verification_narrative      TEXT;

-- Backfill: legacy rows default to GENERAL with both branching flags off
-- (so they sit at the safest path in the new graph).
UPDATE qms_incident
   SET incident_sub_type = 'GENERAL'
 WHERE incident_sub_type IS NULL OR incident_sub_type = '';

CREATE INDEX IF NOT EXISTS idx_inc_spawned_deviation
    ON qms_incident (spawned_deviation_id)
 WHERE spawned_deviation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_inc_sub_type
    ON qms_incident (incident_sub_type);

COMMENT ON COLUMN qms_incident.site_head_required          IS 'Set at Assessment by QA — routes through PENDING_SITE_HEAD when true';
COMMENT ON COLUMN qms_incident.capa_required               IS 'Set at HOD Assessment — drives CAPA cross-link';
COMMENT ON COLUMN qms_incident.linked_capa_number          IS 'CAPA record number generated at HOD Assessment when capa_required = TRUE';
COMMENT ON COLUMN qms_incident.abnormality_remedial_action IS 'Lab + No-Retest path: "Abnormality in Proposed RA" narrative captured by QA Reviewer';
COMMENT ON COLUMN qms_incident.spawned_deviation_id        IS 'Deviation id this Incident spawned (when General + deviation_required = TRUE)';
COMMENT ON COLUMN qms_incident.spawned_deviation_number    IS 'Record number of the spawned Deviation — denormalised for cross-link UI';
COMMENT ON COLUMN qms_incident.verification_narrative      IS 'Closure verification narrative captured at PENDING_VERIFICATION';
