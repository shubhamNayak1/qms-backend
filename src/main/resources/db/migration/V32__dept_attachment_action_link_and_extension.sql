-- Round-N Batch C RED-5 (2026-07-19) — link department attachments to action
-- items and add per-action-item extension-date so the tester's flow works:
--   • Every action item spawned by a dept during Dept Comment becomes an
--     entry the responsible dept must attach evidence against.
--   • If the action item's target date has passed, the dept must first
--     record an extension date before uploading. That extension is a
--     dept-level self-declared field (not a Head-QA-approved workflow).
--   • Multiple attachments per action item — each upload is a new row.
--
-- action_item_id is nullable so legacy per-dept rows (created before this
-- migration) stay valid; new-shape rows are gated on the FK being present.

ALTER TABLE qms_department_attachments
    ADD COLUMN IF NOT EXISTS action_item_id   BIGINT,
    ADD COLUMN IF NOT EXISTS uploaded_by_id   BIGINT,
    ADD COLUMN IF NOT EXISTS uploaded_by_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS uploaded_at      TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_qda_action_item
    ON qms_department_attachments (action_item_id);

ALTER TABLE qms_department_action_items
    ADD COLUMN IF NOT EXISTS extension_date   DATE,
    ADD COLUMN IF NOT EXISTS extension_reason TEXT;
