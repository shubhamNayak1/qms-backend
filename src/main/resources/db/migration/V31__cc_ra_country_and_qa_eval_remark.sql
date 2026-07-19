-- Round-N Batch B (2026-07-19) tester reference-doc structural items
--   S3 — RA "Any Specify Country" narrative captured by Regulatory Affairs.
--   S4 — QA Phase 2 "QA Evaluation Remark" — a summary/verdict captured by
--        QA alongside the Post-Remark, currently missing.
--
-- Both are optional narratives; existing rows stay valid without backfill.

ALTER TABLE qms_change_control
    ADD COLUMN IF NOT EXISTS regulatory_submission_country TEXT,
    ADD COLUMN IF NOT EXISTS qa_evaluation_remark          TEXT;
