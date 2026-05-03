-- ─────────────────────────────────────────────────────────────────────────────
-- V17: Make user.last_name and user.email optional
--
-- Business rules updated:
--   • Surname (last_name) is no longer mandatory.
--   • Email is no longer mandatory (some pharma operators don't have one).
--
-- Email retains its UNIQUE constraint — Postgres treats NULLs as distinct,
-- so multiple users without email are allowed.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE users
    ALTER COLUMN last_name DROP NOT NULL;

ALTER TABLE users
    ALTER COLUMN email DROP NOT NULL;
