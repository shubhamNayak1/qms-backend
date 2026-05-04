-- =============================================================
-- V18__org_structure_and_licensing.sql
--
-- Introduces organisational structure (Sites, Departments) and a
-- licence-based login gate.
--
-- High-level changes:
--   1. New tables: sites, departments, licenses
--   2. New user fields: initials, joining_date, department_id,
--      is_dept_reviewer, is_qa_reviewer
--   3. New qms record fields: department_id, commenting_department_id
--      (added to every per-module record table because each sub-module
--      has its own physical table — see TABLE_PER_CLASS strategy)
--   4. New permissions: ORG_MANAGE, LICENSE_MANAGE
--   5. Default site + canonical departments seeded
--   6. Existing flat roles deprecated (soft-deleted) — SUPER_ADMIN preserved
--   7. All existing users auto-assigned a license so testers don't get
--      locked out by the new login gate
-- =============================================================

-- ───────────────────────────────────────────────────────────────
-- 1. SITES
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sites (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    code        VARCHAR(30)  UNIQUE,
    address     VARCHAR(500),
    head_user_id BIGINT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100)
);

INSERT INTO sites (name, code, address, is_active, created_by)
VALUES ('Default Site', 'SITE-01', 'Update via Settings → Site Profile', TRUE, 'SYSTEM');

-- ───────────────────────────────────────────────────────────────
-- 2. DEPARTMENTS
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS departments (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    code         VARCHAR(30)  NOT NULL,
    description  VARCHAR(500),
    site_id      BIGINT       NOT NULL,
    parent_id    BIGINT,
    hod_user_id  BIGINT,
    dept_type    VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP,
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    CONSTRAINT uq_dept_code      UNIQUE (code),
    CONSTRAINT fk_dept_site      FOREIGN KEY (site_id)   REFERENCES sites(id)        ON DELETE RESTRICT,
    CONSTRAINT fk_dept_parent    FOREIGN KEY (parent_id) REFERENCES departments(id)  ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_dept_site   ON departments (site_id);
CREATE INDEX IF NOT EXISTS idx_dept_parent ON departments (parent_id);
CREATE INDEX IF NOT EXISTS idx_dept_type   ON departments (dept_type);

-- Seed canonical departments for the default site.
INSERT INTO departments (name, code, description, site_id, dept_type, is_active, created_by)
SELECT *, TRUE, 'SYSTEM' FROM (VALUES
    ('Quality Assurance',     'QA',     'Quality Assurance — central reviewer & approver', 1::BIGINT, 'QA'),
    ('Regulatory Affairs',    'RA',     'Regulatory Affairs — RA review step',             1::BIGINT, 'RA'),
    ('Information Technology','IT',     'IT department',                                   1::BIGINT, 'STANDARD'),
    ('Human Resources',       'HR',     'HR department',                                   1::BIGINT, 'STANDARD'),
    ('Production',            'PROD',   'Manufacturing / Production',                      1::BIGINT, 'STANDARD'),
    ('Engineering',           'ENG',    'Engineering & Maintenance',                       1::BIGINT, 'STANDARD'),
    ('Quality Control',       'QC',     'Quality Control labs',                            1::BIGINT, 'STANDARD'),
    ('Warehouse',             'WH',     'Warehouse / Stores',                              1::BIGINT, 'STANDARD'),
    ('General',               'GEN',    'Default catch-all department for legacy users',   1::BIGINT, 'STANDARD')
) AS d(name, code, description, site_id, dept_type);

-- ───────────────────────────────────────────────────────────────
-- 3. LICENSES
-- ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS licenses (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(32)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    assigned_to_user_id BIGINT,
    assigned_at         TIMESTAMP,
    assigned_by_user_id BIGINT,
    expires_at          TIMESTAMP,
    revoked_at          TIMESTAMP,
    revoked_by_user_id  BIGINT,
    notes               VARCHAR(500),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    CONSTRAINT uq_license_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_license_status ON licenses (status);
CREATE INDEX IF NOT EXISTS idx_license_user   ON licenses (assigned_to_user_id);

-- ───────────────────────────────────────────────────────────────
-- 4. USER FIELD ADDITIONS
-- ───────────────────────────────────────────────────────────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS initials          VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS joining_date      DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS department_id     BIGINT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_dept_reviewer  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_qa_reviewer    BOOLEAN NOT NULL DEFAULT FALSE;

-- Best-effort backfill: match users.department string to seeded depts.
UPDATE users u
   SET department_id = d.id
  FROM departments d
 WHERE u.department_id IS NULL
   AND u.department IS NOT NULL
   AND TRIM(LOWER(u.department)) IN (
        TRIM(LOWER(d.name)),
        TRIM(LOWER(d.code))
   );

-- Anything still null gets dropped into General.
UPDATE users
   SET department_id = (SELECT id FROM departments WHERE code = 'GEN')
 WHERE department_id IS NULL;

-- Now we can enforce the FK.
ALTER TABLE users
    ADD CONSTRAINT fk_user_department
    FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_users_department_id ON users (department_id);

-- ───────────────────────────────────────────────────────────────
-- 5. PER-MODULE QMS RECORD TABLES
--    Each sub-module owns its own table (TABLE_PER_CLASS) so we
--    have to add the columns on every one.
-- ───────────────────────────────────────────────────────────────
DO $$
DECLARE
    qms_table TEXT;
BEGIN
    FOREACH qms_table IN ARRAY ARRAY[
        'capa_records',
        'deviation_records',
        'incident_records',
        'change_control_records',
        'market_complaint_records'
    ]
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = qms_table) THEN
            EXECUTE format(
                'ALTER TABLE %I ADD COLUMN IF NOT EXISTS department_id BIGINT', qms_table);
            EXECUTE format(
                'ALTER TABLE %I ADD COLUMN IF NOT EXISTS commenting_department_id BIGINT', qms_table);
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_dept ON %I (department_id)',
                qms_table, qms_table);
        END IF;
    END LOOP;
END $$;

-- ───────────────────────────────────────────────────────────────
-- 6. NEW PERMISSIONS
-- ───────────────────────────────────────────────────────────────
INSERT INTO permissions (name, display_name, module, description, is_deleted, created_at, created_by)
VALUES
    ('ORG_MANAGE',     'Manage Organisation', 'ORG',     'Create / edit sites and departments, set HOD',          FALSE, NOW(), 'SYSTEM'),
    ('LICENSE_MANAGE', 'Manage Licenses',     'LICENSE', 'Generate, assign, and revoke per-user QMS licenses',    FALSE, NOW(), 'SYSTEM'),
    ('USER_BULK_UPLOAD','Bulk Upload Users',  'USER',    'Bulk import users via CSV / JSON',                      FALSE, NOW(), 'SYSTEM')
ON CONFLICT (name) DO NOTHING;

-- Grant the new permissions to SUPER_ADMIN.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.name IN ('ORG_MANAGE', 'LICENSE_MANAGE', 'USER_BULK_UPLOAD')
 WHERE r.name = 'SUPER_ADMIN'
   AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
         WHERE rp.role_id = r.id AND rp.permission_id = p.id
   );

-- ───────────────────────────────────────────────────────────────
-- 7. DEPRECATE THE OLD FLAT ROLES
--    We do NOT delete rows because audit_log entries reference them
--    by name — instead we soft-delete and let SecurityConfig stop
--    issuing them on new accounts.
-- ───────────────────────────────────────────────────────────────
UPDATE roles
   SET is_deleted = TRUE,
       updated_at = NOW(),
       updated_by = 'SYSTEM_V18'
 WHERE name IN ('QA_MANAGER', 'QA_OFFICER', 'AUDITOR', 'HOD');
-- SUPER_ADMIN and EMPLOYEE are intentionally preserved:
--   • SUPER_ADMIN is the system administrator role.
--   • EMPLOYEE is the default flat role for newly created users so other
--     modules' legacy @PreAuthorize checks continue to work. Positional
--     authorisation lives in OrgSecurityService on top.

-- ───────────────────────────────────────────────────────────────
-- 8. SEED LICENSES + AUTO-ASSIGN TO EXISTING USERS
--    Generate one ASSIGNED license per existing user (so testers
--    don't get blocked at login), plus 50 spare AVAILABLE licenses.
-- ───────────────────────────────────────────────────────────────
DO $$
DECLARE
    u RECORD;
    new_code TEXT;
BEGIN
    -- one assigned license per existing user
    FOR u IN SELECT id FROM users WHERE is_deleted = FALSE LOOP
        new_code := 'QMS-' ||
                    UPPER(SUBSTR(MD5(RANDOM()::TEXT || u.id::TEXT), 1, 4)) || '-' ||
                    UPPER(SUBSTR(MD5(RANDOM()::TEXT || u.id::TEXT), 5, 4)) || '-' ||
                    UPPER(SUBSTR(MD5(RANDOM()::TEXT || u.id::TEXT), 9, 4));
        INSERT INTO licenses (code, status, assigned_to_user_id, assigned_at, assigned_by_user_id, notes, created_by)
        VALUES (new_code, 'ASSIGNED', u.id, NOW(), NULL, 'Auto-assigned by V18 migration', 'SYSTEM_V18');
    END LOOP;

    -- 50 spare licenses
    FOR i IN 1..50 LOOP
        new_code := 'QMS-' ||
                    UPPER(SUBSTR(MD5(RANDOM()::TEXT || i::TEXT), 1, 4)) || '-' ||
                    UPPER(SUBSTR(MD5(RANDOM()::TEXT || i::TEXT), 5, 4)) || '-' ||
                    UPPER(SUBSTR(MD5(RANDOM()::TEXT || i::TEXT), 9, 4));
        INSERT INTO licenses (code, status, notes, created_by)
        VALUES (new_code, 'AVAILABLE', 'Pre-generated spare seat', 'SYSTEM_V18');
    END LOOP;
END $$;
