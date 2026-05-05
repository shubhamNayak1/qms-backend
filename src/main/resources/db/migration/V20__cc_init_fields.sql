-- V20: Change-Control "Initiation of Change" tweak
--
-- Two new fields belong on the Create dialog (raised at DRAFT) so the
-- printable VI-Pharma cover sheet has all the operational context the
-- Initiator types up front:
--   • product_material   — "Product / Material" line on the form.
--   • market_details     — markets / regions impacted by the change.
--
-- A third field (linked_capa_number) is filled by the dept HOD during
-- PENDING_HOD review when a related CAPA already exists. We mirror the
-- pattern used by Deviation.linked_capa_number — a free-text reference
-- that the UI links to the CAPA detail page if it matches a known number.

ALTER TABLE qms_change_control
    ADD COLUMN IF NOT EXISTS product_material   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS market_details     TEXT,
    ADD COLUMN IF NOT EXISTS linked_capa_number VARCHAR(30);

COMMENT ON COLUMN qms_change_control.product_material   IS 'Product or material the change applies to (Initiator-supplied)';
COMMENT ON COLUMN qms_change_control.market_details     IS 'Markets / regions impacted by the change (Initiator-supplied)';
COMMENT ON COLUMN qms_change_control.linked_capa_number IS 'Optional CAPA record number this change is linked to (filled at PENDING_HOD)';
