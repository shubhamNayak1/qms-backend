-- Round-N (2026-07-04) tester CC-Point-2 · Issues 1 + 2
-- Change Control HOD Assessment gains a 7-checkbox "Impact" panel
-- (Qualification / Documentation / Validation / Material Source /
-- Regulatory Aspects / Artwork-Pack / Any Other) plus an "Initial
-- Risk Assessment Required?" toggle with a conditional narrative.
--
-- All columns default FALSE / NULL so existing records remain valid
-- without a data-migration step; the frontend simply renders unchecked
-- boxes for historical CCs.

ALTER TABLE qms_change_control
    ADD COLUMN IF NOT EXISTS impact_on_qualification    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS impact_on_documentation    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS impact_on_validation       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS impact_on_material_source  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS impact_regulatory_aspects  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS impact_on_artwork_pack     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS impact_other               BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS impact_other_comment       TEXT,
    ADD COLUMN IF NOT EXISTS initial_risk_assessment_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS initial_risk_assessment    TEXT;
