-- V27: Generic QMS record-attachment store (Round-2 item A1).
--
-- The Round-1 build relied exclusively on linking to a DMS document for
-- record attachments. Tester Round-2 flagged this: many ad-hoc files
-- (Word drafts, JPG photos, internal memos) aren't yet in DMS at the time
-- a Change Control is raised. Forcing them through the DMS check-in flow
-- before they can be attached is the wrong friction.
--
-- This migration adds a small generic table for inline-uploaded files
-- (≤ 10 MB each, configurable on the application side). They sit alongside
-- the DMS link — the QMS record's initial_attachment_ref still supports
-- DMS doc-id references for controlled documents already in the system.
--
-- Identification on the record table uses the same attachment_ref slot but
-- with an "QMS-ATT-{id}" prefix so the response resolver can pick the right
-- backing store. The DMS resolver continues to fire when the prefix is
-- absent and the value parses as an integer.

CREATE TABLE IF NOT EXISTS qms_record_attachments (
    id            BIGSERIAL    PRIMARY KEY,
    -- Polymorphic parent — same convention as qms_department_attachments
    record_type   VARCHAR(40)  NOT NULL,
    record_id     BIGINT       NOT NULL,
    -- File metadata
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(120),
    size_bytes    BIGINT       NOT NULL,
    data          BYTEA        NOT NULL,
    -- Provenance
    uploaded_by_id    BIGINT,
    uploaded_by_name  VARCHAR(160),
    uploaded_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- Soft-delete to keep audit history intact
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at    TIMESTAMP,
    deleted_by_id BIGINT
);

CREATE INDEX IF NOT EXISTS ix_qms_record_attachments_record
    ON qms_record_attachments (record_type, record_id)
 WHERE is_deleted = FALSE;

COMMENT ON TABLE qms_record_attachments IS
    'Round-2 A1 — generic local-file attachment store for QMS records. Sits alongside the DMS link slot on the parent record.';
COMMENT ON COLUMN qms_record_attachments.record_type IS
    'Polymorphic parent enum value (CHANGE_CONTROL, CAPA, DEVIATION, INCIDENT, MARKET_COMPLAINT)';
COMMENT ON COLUMN qms_record_attachments.data IS
    'Raw file bytes — capped at 10 MB by the application layer.';
