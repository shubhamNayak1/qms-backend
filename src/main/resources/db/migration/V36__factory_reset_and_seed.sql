-- =============================================================
-- V36__factory_reset_and_seed.sql (2026-10-15)
--
-- DESTRUCTIVE: wipes every data table and re-seeds the installation
-- with a clean Baseras Tech topology — 1 site, 4 departments,
-- 9 roles, 55 permissions, 30 licences and 13 users (12 real + 1
-- hidden SUPER_ADMIN).
--
-- Preserves table schemas and Flyway history. The caller MUST have
-- taken a Render Postgres snapshot before deploying this migration.
--
-- Everything runs inside the Flyway transaction, so a failure during
-- seeding rolls the entire wipe back.
-- =============================================================

-- ───────────────────────────────────────────────────────────────
-- 0. Schema addition — is_system flag on users.
-- ───────────────────────────────────────────────────────────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_system BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_users_is_system ON users (is_system);

-- ───────────────────────────────────────────────────────────────
-- 1. WIPE every data table. Order via CASCADE handles FKs; identity
--    sequences restart from 1 so the new seed owns the small IDs.
--    Flyway history and schema definitions are untouched.
-- ───────────────────────────────────────────────────────────────
TRUNCATE TABLE
    audit_logs,
    audit_logs_archive,
    password_histories,
    password_policies,
    tcd_policies,
    user_permissions,
    user_roles,
    role_permissions,
    saved_reports,
    report_run_history,
    qms_capa,
    qms_capa_assessments,
    qms_deviation,
    qms_incident,
    qms_change_control,
    qms_market_complaint,
    qms_line_items,
    qms_record_attachments,
    qms_department_comments,
    qms_department_action_items,
    qms_department_attachments,
    qms_audit_schedule,
    dms_documents,
    dms_document_approvals,
    dms_download_logs,
    lms_assessment_attempts,
    lms_assessment_questions,
    lms_assessments,
    lms_certificates,
    lms_compliance_reviews,
    lms_compliance_submissions,
    lms_content_progress,
    lms_enrollments,
    lms_program_contents,
    lms_program_document_links,
    lms_programs,
    lms_training_attendance,
    lms_training_needs,
    lms_training_sessions,
    licenses,
    departments,
    sites,
    users,
    roles,
    permissions
RESTART IDENTITY CASCADE;

-- ───────────────────────────────────────────────────────────────
-- 2. PERMISSIONS CATALOGUE (55 rows — union of V9, V18, V35)
-- ───────────────────────────────────────────────────────────────
INSERT INTO permissions (name, display_name, module, description, is_deleted, created_at, created_by) VALUES
    -- USER
    ('USER_VIEW',              'View Users',             'USER',   'View and search users',                              FALSE, NOW(), 'SYSTEM'),
    ('USER_CREATE',            'Create Users',           'USER',   'Create new user accounts',                           FALSE, NOW(), 'SYSTEM'),
    ('USER_UPDATE',            'Update Users',           'USER',   'Edit user details and profile',                      FALSE, NOW(), 'SYSTEM'),
    ('USER_DELETE',            'Delete Users',           'USER',   'Soft-delete user accounts',                          FALSE, NOW(), 'SYSTEM'),
    ('USER_MANAGE_ROLES',      'Manage User Roles',      'USER',   'Assign and remove roles from users',                 FALSE, NOW(), 'SYSTEM'),
    ('USER_RESET_PASSWORD',    'Reset User Password',    'USER',   'Admin-reset another user password',                  FALSE, NOW(), 'SYSTEM'),
    ('USER_BULK_UPLOAD',       'Bulk Upload Users',      'USER',   'Bulk import users via CSV / JSON',                   FALSE, NOW(), 'SYSTEM'),

    -- QMS
    ('QMS_VIEW',               'View QMS Records',       'QMS',    'View CAPA, deviations, incidents, complaints',       FALSE, NOW(), 'SYSTEM'),
    ('QMS_CREATE',             'Create QMS Records',     'QMS',    'Create new QMS records',                             FALSE, NOW(), 'SYSTEM'),
    ('QMS_UPDATE',             'Update QMS Records',     'QMS',    'Edit existing QMS records',                          FALSE, NOW(), 'SYSTEM'),
    ('QMS_DELETE',             'Delete QMS Records',     'QMS',    'Soft-delete QMS records',                            FALSE, NOW(), 'SYSTEM'),
    ('QMS_APPROVE',            'Approve QMS Records',    'QMS',    'Approve or reject QMS records',                      FALSE, NOW(), 'SYSTEM'),
    ('QMS_ASSIGN',             'Assign QMS Records',     'QMS',    'Assign QMS records to users',                        FALSE, NOW(), 'SYSTEM'),
    ('QMS_EXPORT',             'Export QMS Records',     'QMS',    'Export QMS records / reports',                       FALSE, NOW(), 'SYSTEM'),
    ('QMS_HARD_DELETE',        'Hard-delete QMS',        'QMS',    'Hard-delete QMS records (previously SUPER_ADMIN)',   FALSE, NOW(), 'SYSTEM'),

    -- DMS
    ('DMS_VIEW',               'View Documents',         'DMS',    'View and search documents',                          FALSE, NOW(), 'SYSTEM'),
    ('DMS_UPLOAD',             'Upload Documents',       'DMS',    'Upload new documents',                               FALSE, NOW(), 'SYSTEM'),
    ('DMS_UPDATE',             'Update Documents',       'DMS',    'Edit document metadata and content',                 FALSE, NOW(), 'SYSTEM'),
    ('DMS_DELETE',             'Delete Documents',       'DMS',    'Soft-delete documents',                              FALSE, NOW(), 'SYSTEM'),
    ('DMS_APPROVE',            'Approve Documents',      'DMS',    'Approve and publish documents',                      FALSE, NOW(), 'SYSTEM'),
    ('DMS_DOWNLOAD',           'Download Documents',     'DMS',    'Download document files',                            FALSE, NOW(), 'SYSTEM'),
    ('DMS_HARD_DELETE',        'Hard-delete Documents',  'DMS',    'Hard-delete documents (previously SUPER_ADMIN)',     FALSE, NOW(), 'SYSTEM'),

    -- LMS
    ('LMS_VIEW',               'View Training',          'LMS',    'View training programs and enrollments',             FALSE, NOW(), 'SYSTEM'),
    ('LMS_CREATE',             'Create Training',        'LMS',    'Create new training programs',                       FALSE, NOW(), 'SYSTEM'),
    ('LMS_UPDATE',             'Update Training',        'LMS',    'Edit training programs and content',                 FALSE, NOW(), 'SYSTEM'),
    ('LMS_DELETE',             'Delete Training',        'LMS',    'Delete training programs',                           FALSE, NOW(), 'SYSTEM'),
    ('LMS_ENROLL',             'Enroll Users',           'LMS',    'Enroll users in training programs',                  FALSE, NOW(), 'SYSTEM'),
    ('LMS_ASSESS',             'Take Assessments',       'LMS',    'Take and manage assessments',                        FALSE, NOW(), 'SYSTEM'),
    ('LMS_CERTIFY',            'Issue Certificates',     'LMS',    'Issue and manage certificates',                      FALSE, NOW(), 'SYSTEM'),

    -- REPORT
    ('REPORT_VIEW',            'View Reports',           'REPORT', 'View all reports and dashboards',                    FALSE, NOW(), 'SYSTEM'),
    ('REPORT_EXPORT',          'Export Reports',         'REPORT', 'Export reports to PDF or Excel',                     FALSE, NOW(), 'SYSTEM'),

    -- AUDIT
    ('AUDIT_VIEW',             'View Audit Logs',        'AUDIT',  'View system audit trail',                            FALSE, NOW(), 'SYSTEM'),
    ('AUDIT_EXPORT',           'Export Audit Logs',      'AUDIT',  'Export audit logs',                                  FALSE, NOW(), 'SYSTEM'),

    -- MODULE ACCESS
    ('MOD_DASHBOARD',          'Access Dashboard',       'MOD',    'See the Dashboard page',                             FALSE, NOW(), 'SYSTEM'),
    ('MOD_QMS',                'Access QMS Module',      'MOD',    'See the QMS section in the sidebar',                 FALSE, NOW(), 'SYSTEM'),
    ('MOD_DMS',                'Access DMS Module',      'MOD',    'See the DMS section',                                FALSE, NOW(), 'SYSTEM'),
    ('MOD_LMS',                'Access LMS Module',      'MOD',    'See the LMS section',                                FALSE, NOW(), 'SYSTEM'),
    ('MOD_REPORTS',            'Access Reports',         'MOD',    'See the Reports section',                            FALSE, NOW(), 'SYSTEM'),
    ('MOD_AUDIT',              'Access Audit Trail',     'MOD',    'See the Audit Trail',                                FALSE, NOW(), 'SYSTEM'),

    -- ADMIN — roles & perms
    ('ROLE_VIEW',              'View Roles',             'ADMIN',  'List and view role details',                         FALSE, NOW(), 'SYSTEM'),
    ('ROLE_MANAGE',            'Manage Roles',           'ADMIN',  'Create / edit / disable roles; assign permissions',  FALSE, NOW(), 'SYSTEM'),
    ('PERM_VIEW',              'View Permissions',       'ADMIN',  'See the Permission matrix',                          FALSE, NOW(), 'SYSTEM'),
    ('PERM_MANAGE',            'Manage Permissions',     'ADMIN',  'Grant / revoke user-level permission overrides',     FALSE, NOW(), 'SYSTEM'),

    -- ORG
    ('DEPT_VIEW',              'View Departments',       'ORG',    'List departments',                                   FALSE, NOW(), 'SYSTEM'),
    ('DEPT_MANAGE',            'Manage Departments',     'ORG',    'Create / edit / delete departments',                 FALSE, NOW(), 'SYSTEM'),
    ('SITE_VIEW',              'View Site Profile',      'ORG',    'See site profile details',                           FALSE, NOW(), 'SYSTEM'),
    ('SITE_MANAGE',            'Manage Site Profile',    'ORG',    'Edit site profile; assign site head',                FALSE, NOW(), 'SYSTEM'),
    ('ORG_TREE_VIEW',          'View Org Tree',          'ORG',    'See the organisation tree visualisation',            FALSE, NOW(), 'SYSTEM'),
    ('ORG_MANAGE',             'Manage Organisation',    'ORG',    'Create / edit sites and departments, set HOD',       FALSE, NOW(), 'SYSTEM'),

    -- POLICY
    ('PASSWORD_POLICY_MANAGE', 'Manage Password Policy', 'ADMIN',  'Create / edit the active password policy',           FALSE, NOW(), 'SYSTEM'),
    ('TCD_POLICY_MANAGE',      'Manage TCD Policy',      'ADMIN',  'Update per-module Target Closure Days',              FALSE, NOW(), 'SYSTEM'),

    -- LICENCE
    ('LICENSE_VIEW',           'View Licences',          'ADMIN',  'List licences',                                      FALSE, NOW(), 'SYSTEM'),
    ('LICENSE_MANAGE',         'Manage Licences',        'ADMIN',  'Create / edit licences',                             FALSE, NOW(), 'SYSTEM'),
    ('LICENSE_ASSIGN',         'Assign Licences',        'ADMIN',  'Assign / revoke a licence to a user',                FALSE, NOW(), 'SYSTEM'),

    -- SYSTEM
    ('SYSTEM_OVERRIDE',        'System Override',        'SYSTEM', 'God-mode bit — bypasses all contextual QMS gates',   FALSE, NOW(), 'SYSTEM');

-- ───────────────────────────────────────────────────────────────
-- 3. ROLES (9)
-- ───────────────────────────────────────────────────────────────
INSERT INTO roles (name, display_name, description, is_system_role, is_deleted, created_at, created_by) VALUES
    ('SUPER_ADMIN', 'Super Admin',  'Hidden bootstrap account — all permissions',                 TRUE,  FALSE, NOW(), 'SYSTEM'),
    ('ADMIN',       'Admin',        'Full administrative access to all modules',                  TRUE,  FALSE, NOW(), 'SYSTEM'),
    ('SITE_HEAD',   'Site Head',    'Reviews records that reach the Site Head stage',             TRUE,  FALSE, NOW(), 'SYSTEM'),
    ('RA',          'RA Officer',   'Regulatory Affairs reviewer — RA stage sign-off',            TRUE,  FALSE, NOW(), 'SYSTEM'),
    ('QA',          'QA Officer',   'QA reviewer — acts at the QA stage',                         TRUE,  FALSE, NOW(), 'SYSTEM'),
    ('QA_HEAD',     'QA Head',      'Head of QA — final QA review and sign-off',                  TRUE,  FALSE, NOW(), 'SYSTEM'),
    ('INITIATOR',   'Initiator',    'Opens new QMS records for their department',                 TRUE,  FALSE, NOW(), 'SYSTEM'),
    ('REVIEWER',    'Reviewer',     'Department reviewer — approves initiator work',              TRUE,  FALSE, NOW(), 'SYSTEM'),
    ('HOD',         'HOD',          'Head of Department — final departmental sign-off',           TRUE,  FALSE, NOW(), 'SYSTEM');

-- ───────────────────────────────────────────────────────────────
-- 4. ROLE × PERMISSION MATRIX
-- ───────────────────────────────────────────────────────────────

-- SUPER_ADMIN — every permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN';

-- ADMIN — all admin + modules, including workflow for operational power.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name <> 'SYSTEM_OVERRIDE';

-- SITE_HEAD — read everywhere, approve where in SITE_HEAD stage.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SITE_HEAD' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_LMS','MOD_REPORTS','MOD_AUDIT',
    'QMS_VIEW','QMS_APPROVE','QMS_EXPORT',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS',
    'REPORT_VIEW','REPORT_EXPORT',
    'AUDIT_VIEW','AUDIT_EXPORT',
    'USER_VIEW','DEPT_VIEW','SITE_VIEW','ORG_TREE_VIEW'
);

-- RA — mirror of Site Head; contextual gate restricts action to RA stage.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'RA' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_LMS','MOD_REPORTS','MOD_AUDIT',
    'QMS_VIEW','QMS_APPROVE','QMS_EXPORT',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS',
    'REPORT_VIEW','REPORT_EXPORT',
    'AUDIT_VIEW','AUDIT_EXPORT',
    'USER_VIEW','DEPT_VIEW','SITE_VIEW','ORG_TREE_VIEW'
);

-- QA — view all except Report / Audit / Admin; act in QA stage.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'QA' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_LMS',
    'QMS_VIEW','QMS_APPROVE',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS',
    'USER_VIEW','DEPT_VIEW'
);

-- QA_HEAD — QA + Reports; still no Audit / Admin.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'QA_HEAD' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_LMS','MOD_REPORTS',
    'QMS_VIEW','QMS_APPROVE','QMS_EXPORT',
    'DMS_VIEW','DMS_UPLOAD','DMS_UPDATE','DMS_APPROVE','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS',
    'REPORT_VIEW','REPORT_EXPORT',
    'USER_VIEW','DEPT_VIEW','SITE_VIEW','ORG_TREE_VIEW'
);

-- INITIATOR — view + create across QMS, consume LMS/DMS.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'INITIATOR' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_LMS',
    'QMS_VIEW','QMS_CREATE','QMS_UPDATE',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS',
    'USER_VIEW','DEPT_VIEW'
);

-- REVIEWER — initiator powers + approve-at-dept-reviewer stage.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'REVIEWER' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_LMS',
    'QMS_VIEW','QMS_CREATE','QMS_UPDATE','QMS_APPROVE',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS',
    'USER_VIEW','DEPT_VIEW'
);

-- HOD — same bits as REVIEWER; contextual gate scopes action to HOD stage.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'HOD' AND p.name IN (
    'MOD_DASHBOARD','MOD_QMS','MOD_DMS','MOD_LMS',
    'QMS_VIEW','QMS_CREATE','QMS_UPDATE','QMS_APPROVE',
    'DMS_VIEW','DMS_DOWNLOAD',
    'LMS_VIEW','LMS_ASSESS',
    'USER_VIEW','DEPT_VIEW'
);

-- ───────────────────────────────────────────────────────────────
-- 5. POLICY DEFAULTS
-- ───────────────────────────────────────────────────────────────
INSERT INTO password_policies (
    password_length_min, password_length_max, alpha_min, numeric_min,
    special_char_min, upper_case_min, number_of_login_attempts,
    valid_period, previous_password_attempt_track,
    effective_date, is_deleted, created_at, created_by
) VALUES (
    8, 128, 1, 1, 1, 1, 5, 90, 5, CURRENT_DATE, FALSE, NOW(), 'SYSTEM'
);

INSERT INTO tcd_policies (module_key, max_days, warning_days, extension_allowed, max_extensions, created_by) VALUES
    ('CAPA',             30,  5, TRUE, 1, 'SYSTEM'),
    ('DEVIATION',        60,  7, TRUE, 1, 'SYSTEM'),
    ('INCIDENT',         30,  5, TRUE, 1, 'SYSTEM'),
    ('MARKET_COMPLAINT', 45,  7, TRUE, 1, 'SYSTEM'),
    ('CHANGE_CONTROL',   90, 10, TRUE, 2, 'SYSTEM');

-- ───────────────────────────────────────────────────────────────
-- 6. SITE + DEPARTMENTS
-- ───────────────────────────────────────────────────────────────
INSERT INTO sites (name, code, address, is_active, is_deleted, created_at, created_by) VALUES
    ('Baseras Main Plant', 'BMP01', 'Update via Admin → Site', TRUE, FALSE, NOW(), 'SYSTEM');

INSERT INTO departments (name, code, description, site_id, dept_type, is_active, is_deleted, created_at, created_by)
SELECT d.name, d.code, d.description, s.id, d.dept_type, TRUE, FALSE, NOW(), 'SYSTEM'
FROM sites s
CROSS JOIN (VALUES
    ('General',           'GEN',  'General / cross-functional (Admin, Site Head, RA)', 'STANDARD'),
    ('Production',        'PROD', 'Manufacturing / Production',                        'STANDARD'),
    ('Packaging',         'PKG',  'Packaging',                                         'STANDARD'),
    ('Quality Assurance', 'QA',   'Quality Assurance',                                 'QA')
) AS d(name, code, description, dept_type)
WHERE s.code = 'BMP01';

-- ───────────────────────────────────────────────────────────────
-- 7. USERS — 1 hidden SUPER_ADMIN + 12 real staff.
--    Password for every user = Admin@123 (BCrypt cost=10).
--    must_change_password = TRUE for everyone except the hidden
--    SUPER_ADMIN (which should remain working without a forced reset
--    so bootstrap scripts can still log in).
-- ───────────────────────────────────────────────────────────────
WITH pw AS (
    SELECT '$2b$10$0ZV/iMhUKRd3eKfROLeBhOWsE5jhqqPQnPrOEFzQg6LNKCQxAL2AG'::VARCHAR AS hash
),
dept AS (
    SELECT id, code FROM departments
)
INSERT INTO users (
    username, email, password_hash, first_name, last_name, phone, initials,
    joining_date, department, department_id, designation,
    is_active, is_email_verified, failed_login_attempts,
    must_change_password, is_system, is_deleted, created_at, created_by
)
SELECT u.username, u.email, pw.hash, u.first_name, u.last_name, u.phone, u.initials,
       '2026-10-15'::DATE, u.department, (SELECT id FROM dept WHERE code = u.dept_code), u.designation,
       TRUE, TRUE, 0,
       u.must_change, u.is_system, FALSE, NOW(), 'SYSTEM'
FROM pw
CROSS JOIN (VALUES
    -- Hidden bootstrap account — no dept, must_change_password = FALSE
    ('superadmin',  'superadmin@baserastech.com',    'System',   'Admin',    '+91-9000000000', 'SA',  NULL,    'System',             'Super Admin',    FALSE, TRUE),
    -- General department
    ('r.kapoor',    'rajesh.kapoor@baserastech.com', 'Rajesh',   'Kapoor',   '+91-9811100001', 'RK',  'GEN',   'General',            'Administrator',  TRUE,  FALSE),
    ('v.menon',     'vikram.menon@baserastech.com',  'Vikram',   'Menon',    '+91-9811100002', 'VM',  'GEN',   'General',            'Site Head',      TRUE,  FALSE),
    ('s.pillai',    'sanjay.pillai@baserastech.com', 'Sanjay',   'Pillai',   '+91-9811100003', 'SP',  'GEN',   'General',            'RA Officer',     TRUE,  FALSE),
    -- Production
    ('a.verma',     'ananya.verma@baserastech.com',  'Ananya',   'Verma',    '+91-9811100004', 'AV',  'PROD',  'Production',         'Prod Initiator', TRUE,  FALSE),
    ('d.nair',      'deepak.nair@baserastech.com',   'Deepak',   'Nair',     '+91-9811100005', 'DN',  'PROD',  'Production',         'Prod Reviewer',  TRUE,  FALSE),
    ('k.reddy',     'karthik.reddy@baserastech.com', 'Karthik',  'Reddy',    '+91-9811100006', 'KR',  'PROD',  'Production',         'Prod HOD',       TRUE,  FALSE),
    -- Packaging
    ('n.iyer',      'nisha.iyer@baserastech.com',    'Nisha',    'Iyer',     '+91-9811100007', 'NI',  'PKG',   'Packaging',          'Pkg Initiator',  TRUE,  FALSE),
    ('m.sharma',    'mohan.sharma@baserastech.com',  'Mohan',    'Sharma',   '+91-9811100008', 'MS',  'PKG',   'Packaging',          'Pkg Reviewer',   TRUE,  FALSE),
    ('b.gupta',     'bhavna.gupta@baserastech.com',  'Bhavna',   'Gupta',    '+91-9811100009', 'BG',  'PKG',   'Packaging',          'Pkg HOD',        TRUE,  FALSE),
    -- QA
    ('p.das',       'priya.das@baserastech.com',     'Priya',    'Das',      '+91-9811100010', 'PD',  'QA',    'Quality Assurance',  'QA Officer',     TRUE,  FALSE),
    ('a.khan',      'arun.khan@baserastech.com',     'Arun',     'Khan',     '+91-9811100011', 'AK',  'QA',    'Quality Assurance',  'QA Head',        TRUE,  FALSE)
) AS u(username, email, first_name, last_name, phone, initials, dept_code, department, designation, must_change, is_system);

-- ───────────────────────────────────────────────────────────────
-- 8. USER × ROLE assignments
-- ───────────────────────────────────────────────────────────────
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN (VALUES
    ('superadmin', 'SUPER_ADMIN'),
    ('r.kapoor',   'ADMIN'),
    ('v.menon',    'SITE_HEAD'),
    ('s.pillai',   'RA'),
    ('a.verma',    'INITIATOR'),
    ('d.nair',     'REVIEWER'),
    ('k.reddy',    'HOD'),
    ('n.iyer',     'INITIATOR'),
    ('m.sharma',   'REVIEWER'),
    ('b.gupta',    'HOD'),
    ('p.das',      'QA'),
    ('a.khan',     'QA_HEAD')
) AS m(username, role_name) ON u.username = m.username
JOIN roles r ON r.name = m.role_name;

-- Mark the HODs as the dept head for their department (used by
-- OrgSecurityService to resolve "my department's HOD").
UPDATE departments d SET hod_user_id = u.id
FROM users u
WHERE (d.code = 'PROD' AND u.username = 'k.reddy')
   OR (d.code = 'PKG'  AND u.username = 'b.gupta')
   OR (d.code = 'QA'   AND u.username = 'a.khan');

-- Mark the Site Head on the site row (used for SITE_HEAD stage routing).
UPDATE sites SET head_user_id = (SELECT id FROM users WHERE username = 'v.menon')
WHERE code = 'BMP01';

-- The QA Head is also a QA reviewer by default (contextual gate).
UPDATE users SET is_qa_reviewer = TRUE
WHERE username IN ('a.khan','p.das');

-- The Dept Reviewers are the REVIEWER-role users of their dept.
UPDATE users SET is_dept_reviewer = TRUE
WHERE username IN ('d.nair','m.sharma');

-- ───────────────────────────────────────────────────────────────
-- 9. LICENCES — 30 slots, 12 assigned to the 12 non-system users,
--    18 remain AVAILABLE.
-- ───────────────────────────────────────────────────────────────
INSERT INTO licenses (code, status, assigned_to_user_id, assigned_at, expires_at,
                      is_deleted, created_at, created_by)
SELECT
    'LIC-' || LPAD(gs::TEXT, 4, '0'),
    CASE WHEN gs <= 12 THEN 'ASSIGNED' ELSE 'AVAILABLE' END,
    CASE WHEN gs <= 12
         THEN (SELECT id FROM users
                WHERE is_system = FALSE
                ORDER BY id
                OFFSET (gs - 1) LIMIT 1)
         ELSE NULL END,
    CASE WHEN gs <= 12 THEN NOW() ELSE NULL END,
    '2027-10-15'::TIMESTAMP,
    FALSE, NOW(), 'SYSTEM'
FROM generate_series(1, 30) AS gs;
