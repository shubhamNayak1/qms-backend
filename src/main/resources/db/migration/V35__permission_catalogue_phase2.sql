-- Admin module Phase 2 (2026-10-09) — permission catalogue expansion.
--
-- Companion to docs/permissions.md. Three things:
--   1. Seeds the 14 new permissions (6 MOD_*, 4 role/perm admin,
--      5 organisation, 2 policy, 3 licence, 1 system, 2 hard-delete,
--      2 QMS extras — 14 of them that aren't already in V9).
--   2. Creates the user_permissions override table (grant/revoke
--      overlays on top of role_permissions).
--   3. Writes the default Role × Permission mappings for the 7
--      seeded roles so no existing user loses access on upgrade.

-- ────────────────────────────────────────────────────────────
-- 1. New permissions
-- ────────────────────────────────────────────────────────────
INSERT INTO permissions (name, display_name, module, description, is_deleted, created_at, created_by)
VALUES
  -- Module access
  ('MOD_DASHBOARD',          'Access Dashboard',       'MOD',   'See the Dashboard page',                              FALSE, NOW(), 'SYSTEM'),
  ('MOD_QMS',                'Access QMS Module',      'MOD',   'See the QMS section in the sidebar',                  FALSE, NOW(), 'SYSTEM'),
  ('MOD_DMS',                'Access DMS Module',      'MOD',   'See the DMS section',                                 FALSE, NOW(), 'SYSTEM'),
  ('MOD_LMS',                'Access LMS Module',      'MOD',   'See the LMS section',                                 FALSE, NOW(), 'SYSTEM'),
  ('MOD_REPORTS',            'Access Reports',         'MOD',   'See the Reports section',                             FALSE, NOW(), 'SYSTEM'),
  ('MOD_AUDIT',              'Access Audit Trail',     'MOD',   'See the Audit Trail',                                 FALSE, NOW(), 'SYSTEM'),

  -- User-admin extra
  ('USER_RESET_PASSWORD',    'Reset User Password',    'USER',  'Admin-reset another user''s password',                FALSE, NOW(), 'SYSTEM'),

  -- Role & permission admin
  ('ROLE_VIEW',              'View Roles',             'ADMIN', 'List and view role details',                          FALSE, NOW(), 'SYSTEM'),
  ('ROLE_MANAGE',            'Manage Roles',           'ADMIN', 'Create / edit / disable roles; assign permissions',   FALSE, NOW(), 'SYSTEM'),
  ('PERM_VIEW',              'View Permissions',       'ADMIN', 'See the Permission matrix',                           FALSE, NOW(), 'SYSTEM'),
  ('PERM_MANAGE',            'Manage Permissions',     'ADMIN', 'Grant / revoke user-level permission overrides',      FALSE, NOW(), 'SYSTEM'),

  -- Organisation admin
  ('DEPT_VIEW',              'View Departments',       'ORG',   'List departments',                                    FALSE, NOW(), 'SYSTEM'),
  ('DEPT_MANAGE',            'Manage Departments',     'ORG',   'Create / edit / delete departments',                  FALSE, NOW(), 'SYSTEM'),
  ('SITE_VIEW',              'View Site Profile',      'ORG',   'See site profile details',                            FALSE, NOW(), 'SYSTEM'),
  ('SITE_MANAGE',            'Manage Site Profile',    'ORG',   'Edit site profile; assign site head',                 FALSE, NOW(), 'SYSTEM'),
  ('ORG_TREE_VIEW',          'View Org Tree',          'ORG',   'See the organisation tree visualisation',             FALSE, NOW(), 'SYSTEM'),

  -- Policy admin
  ('PASSWORD_POLICY_MANAGE', 'Manage Password Policy', 'ADMIN', 'Create / edit the active password policy',            FALSE, NOW(), 'SYSTEM'),
  ('TCD_POLICY_MANAGE',      'Manage TCD Policy',      'ADMIN', 'Update per-module Target Closure Days',               FALSE, NOW(), 'SYSTEM'),

  -- Licence admin
  ('LICENSE_VIEW',           'View Licences',          'ADMIN', 'List licences',                                       FALSE, NOW(), 'SYSTEM'),
  ('LICENSE_MANAGE',         'Manage Licences',        'ADMIN', 'Create / edit licences',                              FALSE, NOW(), 'SYSTEM'),
  ('LICENSE_ASSIGN',         'Assign Licences',        'ADMIN', 'Assign / revoke a licence to a user',                 FALSE, NOW(), 'SYSTEM'),

  -- QMS hard delete + export
  ('QMS_HARD_DELETE',        'Hard-delete QMS',        'QMS',   'Soft-delete QMS records (previously SUPER_ADMIN)',    FALSE, NOW(), 'SYSTEM'),
  ('QMS_EXPORT',             'Export QMS Records',     'QMS',   'Export QMS records / reports',                        FALSE, NOW(), 'SYSTEM'),

  -- DMS hard delete
  ('DMS_HARD_DELETE',        'Hard-delete Documents',  'DMS',   'Soft-delete documents (previously SUPER_ADMIN)',      FALSE, NOW(), 'SYSTEM'),

  -- System override
  ('SYSTEM_OVERRIDE',        'System Override',        'SYSTEM','God-mode bit — bypasses all contextual QMS gates',    FALSE, NOW(), 'SYSTEM')
ON CONFLICT (name) DO NOTHING;

-- ────────────────────────────────────────────────────────────
-- 2. user_permissions override table
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id       BIGINT       NOT NULL,
    permission_id BIGINT       NOT NULL,
    grant_type    VARCHAR(10)  NOT NULL,
    granted_by    VARCHAR(100),
    granted_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    reason        VARCHAR(500),
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_up_user       FOREIGN KEY (user_id)       REFERENCES users(id)       ON DELETE CASCADE,
    CONSTRAINT fk_up_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    CONSTRAINT ck_up_grant_type CHECK (grant_type IN ('GRANT','REVOKE'))
);

CREATE INDEX IF NOT EXISTS idx_up_user ON user_permissions (user_id);
CREATE INDEX IF NOT EXISTS idx_up_perm ON user_permissions (permission_id);

-- ────────────────────────────────────────────────────────────
-- 3. Default Role × Permission grants
--    (reviewed in docs/permissions.md § "Default Role × Permission mapping")
-- ────────────────────────────────────────────────────────────

-- Clear existing SUPER_ADMIN grants so they can be re-asserted
-- (SUPER_ADMIN gets ALL permissions after this migration — same as V1
-- intent, but it also picks up every new perm added here).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

-- ADMIN — all admin ops, zero workflow access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name IN (
    'MOD_DASHBOARD','MOD_AUDIT',
    'USER_VIEW','USER_CREATE','USER_UPDATE','USER_DELETE',
    'USER_MANAGE_ROLES','USER_RESET_PASSWORD',
    'ROLE_VIEW','ROLE_MANAGE',
    'PERM_VIEW','PERM_MANAGE',
    'DEPT_VIEW','DEPT_MANAGE',
    'SITE_VIEW','SITE_MANAGE',
    'ORG_TREE_VIEW',
    'PASSWORD_POLICY_MANAGE','TCD_POLICY_MANAGE',
    'LICENSE_VIEW','LICENSE_MANAGE','LICENSE_ASSIGN',
    'AUDIT_VIEW'
)
ON CONFLICT DO NOTHING;

-- QA_MANAGER — full workflow + viewing admin
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'QA_MANAGER' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_LMS','MOD_REPORTS','MOD_AUDIT',
    'QMS_VIEW','QMS_CREATE','QMS_UPDATE','QMS_APPROVE','QMS_ASSIGN','QMS_EXPORT',
    'DMS_VIEW','DMS_UPLOAD','DMS_UPDATE','DMS_APPROVE','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ENROLL',
    'REPORT_VIEW','REPORT_EXPORT',
    'AUDIT_VIEW',
    'USER_VIEW','DEPT_VIEW','SITE_VIEW','ORG_TREE_VIEW'
)
ON CONFLICT DO NOTHING;

-- QA_OFFICER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'QA_OFFICER' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_REPORTS',
    'QMS_VIEW','QMS_CREATE','QMS_UPDATE',
    'DMS_VIEW','DMS_UPLOAD','DMS_UPDATE','DMS_DOWNLOAD',
    'REPORT_VIEW'
)
ON CONFLICT DO NOTHING;

-- AUDITOR — read-only + audit export
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'AUDITOR' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_LMS','MOD_REPORTS','MOD_AUDIT',
    'QMS_VIEW',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW',
    'REPORT_VIEW',
    'AUDIT_VIEW','AUDIT_EXPORT'
)
ON CONFLICT DO NOTHING;

-- DOC_CONTROLLER — full DMS less hard-delete
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'DOC_CONTROLLER' AND p.name IN (
    'MOD_DASHBOARD','MOD_DMS','MOD_REPORTS',
    'DMS_VIEW','DMS_UPLOAD','DMS_UPDATE','DMS_APPROVE','DMS_DOWNLOAD',
    'REPORT_VIEW'
)
ON CONFLICT DO NOTHING;

-- EMPLOYEE — basic initiate + training
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'EMPLOYEE' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_LMS',
    'QMS_VIEW','QMS_CREATE',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS'
)
ON CONFLICT DO NOTHING;

-- SYSTEM_OVERRIDE goes to SUPER_ADMIN only (covered by the wildcard
-- grant above). ADMIN explicitly does NOT get this bit — the whole
-- point of SUPER_ADMIN is that it alone bypasses workflow gates.
