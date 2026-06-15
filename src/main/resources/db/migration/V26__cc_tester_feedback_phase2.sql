-- V26: Change Control tester-feedback phase 2 (Round-2 walkthrough)
--
-- ── initial_assessment ───────────────────────────────────────────
-- The Round-1 build co-located the HOD's "Initial Assessment" with QA's
-- "Risk Assessment" narrative under a single risk_assessment column. The
-- Round-2 tester walkthrough caught that this causes the QA Phase-2
-- Risk Assessment textarea to pre-populate with the HOD's text (item F1)
-- because both stages read/write the same column.
--
-- Split into two columns:
--   • initial_assessment — written by HOD during HOD Assessment.
--                          Read-only on every downstream stage.
--   • risk_assessment    — written by QA during QA Phase 2 when
--                          riskAssessmentRequired = TRUE.
--                          Stays NULL when QA decides risk assessment
--                          isn't needed.
--
-- We back-fill initial_assessment from the existing risk_assessment
-- column so legacy records keep their HOD narrative on display, then
-- null out risk_assessment for legacy records that hadn't reached QA
-- yet (DRAFT / PENDING_HOD / PENDING_QA_REVIEW). Records past QA keep
-- both columns populated.

ALTER TABLE qms_change_control
    ADD COLUMN IF NOT EXISTS initial_assessment TEXT;

ALTER TABLE qms_capa             ADD COLUMN IF NOT EXISTS initial_assessment TEXT;
ALTER TABLE qms_deviation        ADD COLUMN IF NOT EXISTS initial_assessment TEXT;
ALTER TABLE qms_incident         ADD COLUMN IF NOT EXISTS initial_assessment TEXT;
ALTER TABLE qms_market_complaint ADD COLUMN IF NOT EXISTS initial_assessment TEXT;

-- Back-fill: any record that hasn't yet reached QA Phase 2 has only the
-- HOD's text in risk_assessment, so move it. Records already past QA
-- need a copy so the HOD's text isn't lost.
UPDATE qms_change_control
   SET initial_assessment = risk_assessment
 WHERE initial_assessment IS NULL
   AND risk_assessment IS NOT NULL;

UPDATE qms_capa
   SET initial_assessment = risk_assessment
 WHERE initial_assessment IS NULL
   AND risk_assessment IS NOT NULL;

UPDATE qms_deviation
   SET initial_assessment = risk_assessment
 WHERE initial_assessment IS NULL
   AND risk_assessment IS NOT NULL;

UPDATE qms_incident
   SET initial_assessment = risk_assessment
 WHERE initial_assessment IS NULL
   AND risk_assessment IS NOT NULL;

UPDATE qms_market_complaint
   SET initial_assessment = risk_assessment
 WHERE initial_assessment IS NULL
   AND risk_assessment IS NOT NULL;

-- Records still at the HOD stage haven't been touched by QA yet — clear
-- their risk_assessment so QA Phase 2 opens with a blank textarea.
UPDATE qms_change_control SET risk_assessment = NULL
 WHERE status IN ('DRAFT', 'PENDING_HOD', 'PENDING_QA_REVIEW');

UPDATE qms_capa             SET risk_assessment = NULL
 WHERE status IN ('DRAFT', 'PENDING_HOD', 'PENDING_QA_REVIEW');

UPDATE qms_deviation        SET risk_assessment = NULL
 WHERE status IN ('DRAFT', 'PENDING_HOD', 'PENDING_QA_REVIEW');

UPDATE qms_incident         SET risk_assessment = NULL
 WHERE status IN ('DRAFT', 'PENDING_HOD', 'PENDING_QA_REVIEW');

UPDATE qms_market_complaint SET risk_assessment = NULL
 WHERE status IN ('DRAFT', 'PENDING_HOD', 'PENDING_INVESTIGATION');

COMMENT ON COLUMN qms_change_control.initial_assessment IS 'HOD Initial Assessment narrative — written during HOD Assessment, read-only on downstream stages. Separated from risk_assessment per Round-2 item F1.';
COMMENT ON COLUMN qms_capa.initial_assessment            IS 'HOD Initial Assessment narrative — read-only downstream. Separated from risk_assessment per Round-2 item F1.';
COMMENT ON COLUMN qms_deviation.initial_assessment       IS 'HOD Initial Assessment narrative — read-only downstream. Separated from risk_assessment per Round-2 item F1.';
COMMENT ON COLUMN qms_incident.initial_assessment        IS 'HOD Initial Assessment narrative — read-only downstream. Separated from risk_assessment per Round-2 item F1.';
COMMENT ON COLUMN qms_market_complaint.initial_assessment IS 'HOD Initial Assessment narrative — read-only downstream. Separated from risk_assessment per Round-2 item F1.';
