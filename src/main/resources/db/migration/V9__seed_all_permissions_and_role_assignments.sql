-- =============================================================
-- V9__seed_all_permissions_and_role_assignments.sql
-- Seeds all permissions for every module and assigns them
-- to the correct roles.
-- =============================================================

-- ─── INSERT ALL PERMISSIONS ───────────────────────────────────

INSERT INTO permissions (name, display_name, module, description, is_deleted, created_at, created_by)
VALUES
  -- USER module
  ('USER_VIEW',         'View Users',            'USER',   'View and search users',                        FALSE, NOW(), 'SYSTEM'),
  ('USER_CREATE',       'Create Users',          'USER',   'Create new user accounts',                     FALSE, NOW(), 'SYSTEM'),
  ('USER_UPDATE',       'Update Users',          'USER',   'Edit user details and profile',                FALSE, NOW(), 'SYSTEM'),
  ('USER_DELETE',       'Delete Users',          'USER',   'Soft-delete user accounts',                    FALSE, NOW(), 'SYSTEM'),
  ('USER_MANAGE_ROLES', 'Manage User Roles',     'USER',   'Assign and remove roles from users',           FALSE, NOW(), 'SYSTEM'),

  -- QMS module
  ('QMS_VIEW',          'View QMS Records',      'QMS',    'View CAPA, deviations, incidents, complaints', FALSE, NOW(), 'SYSTEM'),
  ('QMS_CREATE',        'Create QMS Records',    'QMS',    'Create new QMS records',                       FALSE, NOW(), 'SYSTEM'),
  ('QMS_UPDATE',        'Update QMS Records',    'QMS',    'Edit existing QMS records',                    FALSE, NOW(), 'SYSTEM'),
  ('QMS_DELETE',        'Delete QMS Records',    'QMS',    'Soft-delete QMS records',                      FALSE, NOW(), 'SYSTEM'),
  ('QMS_APPROVE',       'Approve QMS Records',   'QMS',    'Approve or reject QMS records',                FALSE, NOW(), 'SYSTEM'),
  ('QMS_ASSIGN',        'Assign QMS Records',    'QMS',    'Assign QMS records to users',                  FALSE, NOW(), 'SYSTEM'),

  -- DMS module
  ('DMS_VIEW',          'View Documents',        'DMS',    'View and search documents',                    FALSE, NOW(), 'SYSTEM'),
  ('DMS_UPLOAD',        'Upload Documents',      'DMS',    'Upload new documents',                         FALSE, NOW(), 'SYSTEM'),
  ('DMS_UPDATE',        'Update Documents',      'DMS',    'Edit document metadata and content',           FALSE, NOW(), 'SYSTEM'),
  ('DMS_DELETE',        'Delete Documents',      'DMS',    'Soft-delete documents',                        FALSE, NOW(), 'SYSTEM'),
  ('DMS_APPROVE',       'Approve Documents',     'DMS',    'Approve and publish documents',                FALSE, NOW(), 'SYSTEM'),
  ('DMS_DOWNLOAD',      'Download Documents',    'DMS',    'Download document files',                      FALSE, NOW(), 'SYSTEM'),

  -- LMS module
  ('LMS_VIEW',          'View Training',         'LMS',    'View training programs and enrollments',       FALSE, NOW(), 'SYSTEM'),
  ('LMS_CREATE',        'Create Training',       'LMS',    'Create new training programs',                 FALSE, NOW(), 'SYSTEM'),
  ('LMS_UPDATE',        'Update Training',       'LMS',    'Edit training programs and content',           FALSE, NOW(), 'SYSTEM'),
  ('LMS_DELETE',        'Delete Training',       'LMS',    'Delete training programs',                     FALSE, NOW(), 'SYSTEM'),
  ('LMS_ENROLL',        'Enroll Users',          'LMS',    'Enroll users in training programs',            FALSE, NOW(), 'SYSTEM'),
  ('LMS_ASSESS',        'Take Assessments',      'LMS',    'Take and manage assessments',                  FALSE, NOW(), 'SYSTEM'),
  ('LMS_CERTIFY',       'Issue Certificates',    'LMS',    'Issue and manage certificates',                FALSE, NOW(), 'SYSTEM'),

  -- REPORT module
  ('REPORT_VIEW',       'View Reports',          'REPORT', 'View all reports and dashboards',              FALSE, NOW(), 'SYSTEM'),
  ('REPORT_EXPORT',     'Export Reports',        'REPORT', 'Export reports to PDF or Excel',               FALSE, NOW(), 'SYSTEM'),

  -- AUDIT module
  ('AUDIT_VIEW',        'View Audit Logs',       'AUDIT',  'View system audit trail',                      FALSE, NOW(), 'SYSTEM'),
  ('AUDIT_EXPORT',      'Export Audit Logs',     'AUDIT',  'Export audit logs',                            FALSE, NOW(), 'SYSTEM')

ON CONFLICT (name) DO NOTHING;

-- =============================================================
-- ROLE → PERMISSION ASSIGNMENTS
-- =============================================================

-- ─── SUPER_ADMIN: all permissions (CROSS JOIN) ────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN'
  AND p.is_deleted = FALSE
ON CONFLICT DO NOTHING;

-- ─── QA_MANAGER ───────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'QA_MANAGER'
  AND p.name IN (
    'USER_VIEW','USER_CREATE','USER_UPDATE','USER_DELETE','USER_MANAGE_ROLES',
    'QMS_VIEW','QMS_CREATE','QMS_UPDATE','QMS_DELETE','QMS_APPROVE','QMS_ASSIGN',
    'DMS_VIEW','DMS_UPLOAD','DMS_UPDATE','DMS_DELETE','DMS_APPROVE','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_CREATE','LMS_UPDATE','LMS_DELETE','LMS_ENROLL','LMS_ASSESS','LMS_CERTIFY',
    'REPORT_VIEW','REPORT_EXPORT',
    'AUDIT_VIEW','AUDIT_EXPORT'
  )
ON CONFLICT DO NOTHING;

-- ─── QA_OFFICER ───────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'QA_OFFICER'
  AND p.name IN (
    'QMS_VIEW','QMS_CREATE','QMS_UPDATE','QMS_ASSIGN',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS'
  )
ON CONFLICT DO NOTHING;

-- ─── AUDITOR ──────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'AUDITOR'
  AND p.name IN (
    'QMS_VIEW',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW',
    'REPORT_VIEW','REPORT_EXPORT',
    'AUDIT_VIEW','AUDIT_EXPORT'
  )
ON CONFLICT DO NOTHING;

-- ─── DOC_CONTROLLER ───────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'DOC_CONTROLLER'
  AND p.name IN (
    'DMS_VIEW','DMS_UPLOAD','DMS_UPDATE','DMS_DELETE','DMS_APPROVE','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS'
  )
ON CONFLICT DO NOTHING;

-- ─── EMPLOYEE ─────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'EMPLOYEE'
  AND p.name IN (
    'QMS_VIEW','QMS_CREATE','QMS_UPDATE',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS'
  )
ON CONFLICT DO NOTHING;
