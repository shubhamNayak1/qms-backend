-- =============================================================
-- V19__qms_common_fields_and_child_tables.sql
--
-- Aligns every QMS sub-module on the same form structure that
-- the existing VI-Pharma Change Control system uses:
--
--   • Repeating "Existing System / Proposed System / Justification"
--     line items   → qms_line_items
--   • Department-wise comments (multi-dept review fan-out)
--                  → qms_department_comments
--   • Common form fields on every record table:
--       risk_assessment, category, customer comm block,
--       verification phase, target-date extension
--
-- The columns are added to all 5 per-module tables since each
-- sub-module owns its own physical table (TABLE_PER_CLASS).
-- =============================================================

-- ───────────────────────────────────────────────────────────────
-- 1. SHARED CHILD TABLES
-- ───────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS qms_line_items (
    id                 BIGSERIAL PRIMARY KEY,
    record_type        VARCHAR(30) NOT NULL,
    record_id          BIGINT      NOT NULL,
    sr_no              INTEGER     NOT NULL,
    existing_system    TEXT,
    proposed_system    TEXT,
    justification      TEXT,
    proposed_by_id     BIGINT,
    proposed_by_name   VARCHAR(150),
    proposed_date      DATE,
    status             VARCHAR(30),
    remark             TEXT,
    checked_by_id      BIGINT,
    checked_by_name    VARCHAR(150),
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_qli_record    ON qms_line_items (record_type, record_id);
CREATE INDEX IF NOT EXISTS idx_qli_record_id ON qms_line_items (record_id);

CREATE TABLE IF NOT EXISTS qms_department_comments (
    id              BIGSERIAL PRIMARY KEY,
    record_type     VARCHAR(30)  NOT NULL,
    record_id       BIGINT       NOT NULL,
    department_id   BIGINT       NOT NULL,
    department_name VARCHAR(150) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    comment         TEXT,
    done_by_id      BIGINT,
    done_by_name    VARCHAR(150),
    done_at         TIMESTAMP,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT fk_qdc_dept FOREIGN KEY (department_id)
        REFERENCES departments(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_qdc_record ON qms_department_comments (record_type, record_id);
CREATE INDEX IF NOT EXISTS idx_qdc_dept   ON qms_department_comments (department_id);
CREATE INDEX IF NOT EXISTS idx_qdc_status ON qms_department_comments (status);

-- ───────────────────────────────────────────────────────────────
-- 2. ADD COMMON COLUMNS TO EVERY PER-MODULE RECORD TABLE
-- ───────────────────────────────────────────────────────────────
DO $$
DECLARE
    qms_table TEXT;
BEGIN
    -- NOTE: real per-module table names are `qms_<module>`, not `<module>_records`.
    -- V18 used the wrong names; the V18 ALTERs were silently skipped on those
    -- tables. We add ALL the V18 + V19 columns here under the correct names so
    -- everything ends up consistent.
    FOREACH qms_table IN ARRAY ARRAY[
        'qms_capa',
        'qms_deviation',
        'qms_incident',
        'qms_change_control',
        'qms_market_complaint'
    ]
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = qms_table) THEN
            -- Org structure FKs (originally V18, missed due to table-name bug)
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS department_id BIGINT',            qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS commenting_department_id BIGINT',  qms_table);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_dept ON %I (department_id)',
                           qms_table, qms_table);

            -- Risk + categorisation
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS risk_assessment TEXT',                 qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS category VARCHAR(20)',                 qms_table);
            -- Customer communication block
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS customer_communication_required BOOLEAN', qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS customer_representative VARCHAR(150)', qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS customer_comment TEXT',                qms_table);
            -- Verification phase
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS verification_action_taken TEXT',       qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS verification_effective_on DATE',       qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS verification_documents_reissue BOOLEAN', qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS verification_other_comments TEXT',     qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS verification_reg_communication TEXT',  qms_table);
            -- Target-date extension (lightweight inline workflow)
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS target_date_extension_date DATE',         qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS target_date_extension_reason TEXT',       qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS target_date_extension_status VARCHAR(20)',qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS target_date_extension_requested_by_id BIGINT', qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS target_date_extension_requested_at TIMESTAMP', qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS target_date_extension_decided_by_id BIGINT',   qms_table);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS target_date_extension_decided_at TIMESTAMP',   qms_table);
        END IF;
    END LOOP;
END $$;
