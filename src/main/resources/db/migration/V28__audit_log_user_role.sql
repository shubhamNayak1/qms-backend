-- Round-M (2026-06-27) Tester feedback CC-Point-1 · Issue 2:
-- The frontend Audit Trail table has a "Role" column, but the AuditLog
-- entity had no corresponding field so the column always rendered as
-- "—". Adding a user_role column so the AOP audit interceptor can
-- populate the role from the Spring Security principal.
--
-- Stored as VARCHAR(120) to accommodate comma-separated role lists
-- (a user may hold multiple roles such as "QA_REVIEWER,SUPER_ADMIN").
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS user_role VARCHAR(120);

-- Index the role column so per-role queries in the Audit Trail page
-- (already exposed via the ROLE filter on the frontend) can leverage it.
CREATE INDEX IF NOT EXISTS idx_al_user_role ON audit_logs (user_role);
