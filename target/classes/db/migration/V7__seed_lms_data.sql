-- =============================================================
-- V7__seed_lms_data.sql (PostgreSQL FIXED)
-- =============================================================

-- =============================================================
-- Programs
-- =============================================================
INSERT INTO lms_programs
    (code, title, description, category, department, tags, status,
     is_mandatory, estimated_duration_minutes, certificate_validity_years,
     completion_deadline_days, assessment_required, pass_score, max_attempts,
     owner_name, is_deleted, created_at, created_by)
VALUES
    ('GMP-001',
     'GMP Awareness Training — Level 1',
     'Mandatory annual GMP awareness training covering ALCOA+, documentation, and hygiene requirements.',
     'GMP', NULL, 'GMP,mandatory,awareness,ALCOA',
     'ACTIVE', TRUE, 90, 2, 30, TRUE, 80, 3,
     'Training Manager', FALSE, NOW(), 'SYSTEM'),

    ('SAF-001',
     'Safety Induction',
     'Mandatory safety induction for all new starters.',
     'Safety', NULL, 'safety,induction,mandatory',
     'ACTIVE', TRUE, 60, 1, 7, TRUE, 80, 2,
     'EHS Manager', FALSE, NOW(), 'SYSTEM')

ON CONFLICT (code) DO NOTHING;


-- =============================================================
-- Content items (GMP-001) — SAFE INSERT
-- =============================================================
INSERT INTO lms_program_contents
    (program_id, content_type, title, description, display_order,
     is_required, duration_minutes, created_at)
SELECT p.id, 'TEXT',
       'What is GMP?',
       'Introduction to GMP.',
       1, TRUE, 15, NOW()
FROM lms_programs p
WHERE p.code = 'GMP-001'
AND NOT EXISTS (
    SELECT 1 FROM lms_program_contents c
    WHERE c.program_id = p.id AND c.display_order = 1
);


INSERT INTO lms_program_contents
    (program_id, content_type, title, description, display_order,
     is_required, duration_minutes, content_url, created_at)
SELECT p.id, 'VIDEO',
       'ALCOA+ Principles',
       'Explains ALCOA+',
       2, TRUE, 20,
       'https://training.qms.com/videos/alcoa.mp4',
       NOW()
FROM lms_programs p
WHERE p.code = 'GMP-001'
AND NOT EXISTS (
    SELECT 1 FROM lms_program_contents c
    WHERE c.program_id = p.id AND c.display_order = 2
);


-- =============================================================
-- Enrollments (FK SAFE - use existing users)
-- =============================================================
INSERT INTO lms_enrollments
    (user_id, user_name, user_email, user_department, program_id,
     status, due_date, progress_percent, attempts_used,
     assigned_by_name, assignment_reason,
     is_deleted, created_at, created_by)
SELECT
    u.id,
    u.username,
    u.email,
    u.department,
    p.id,
    'IN_PROGRESS',
    CURRENT_DATE + INTERVAL '14 days',
    65,
    0,
    'Training Manager',
    'Annual GMP refresher',
    FALSE,
    NOW(),
    'SYSTEM'
FROM lms_programs p
JOIN users u ON u.username = 'admin'   -- SAFE USER
WHERE p.code = 'GMP-001'
ON CONFLICT (user_id, program_id) DO NOTHING;


INSERT INTO lms_enrollments
    (user_id, user_name, user_email, user_department, program_id,
     status, due_date, progress_percent, attempts_used,
     assigned_by_name, assignment_reason,
     is_deleted, created_at, created_by)
SELECT
    u.id,
    u.username,
    u.email,
    u.department,
    p.id,
    'COMPLETED',
    CURRENT_DATE + INTERVAL '30 days',
    100,
    1,
    'QA Manager',
    'Annual GMP refresher',
    FALSE,
    NOW() - INTERVAL '10 days',
    'SYSTEM'
FROM lms_programs p
JOIN users u ON u.username = 'admin'
WHERE p.code = 'GMP-001'
ON CONFLICT (user_id, program_id) DO NOTHING;


-- =============================================================
-- Certificate (SAFE JOIN)
-- =============================================================
INSERT INTO lms_certificates
    (enrollment_id, user_id, user_name, program_id, program_title, program_code,
     certificate_number, issuer, issued_date, expiry_date,
     status, score_achieved, created_at)
SELECT
    e.id,
    e.user_id,
    e.user_name,
    e.program_id,
    p.title,
    p.code,
    'CERT-GMP-' || e.user_id || '-' || TO_CHAR(CURRENT_DATE, 'YYYYMM'),
    'QMS QA Department',
    CURRENT_DATE,
    CURRENT_DATE + INTERVAL '2 years',
    'ACTIVE',
    88,
    NOW()
FROM lms_enrollments e
JOIN lms_programs p ON p.id = e.program_id
WHERE p.code = 'GMP-001'
AND e.status = 'COMPLETED'
ON CONFLICT (certificate_number) DO NOTHING;