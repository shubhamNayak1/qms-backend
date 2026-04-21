-- =============================================================
-- V6__seed_dms_data.sql (PostgreSQL FIXED)
-- =============================================================

-- =============================================================
-- Seed Documents
-- =============================================================
INSERT INTO dms_documents
    (doc_number, version, major_version, minor_version,
     title, description, category, status, access_level,
     department, tags, author_name, owner_name,
     original_filename, storage_key, mime_type, file_size_bytes, sha256_checksum,
     effective_date, expiry_date, review_date,
     is_controlled, download_count, is_deleted, created_at, created_by)
VALUES

-- EFFECTIVE SOP
('DOC-SOP-202401-0001', '2.0', 2, 0,
 'Environmental Monitoring SOP — Cleanroom Grade A/B',
 'Procedure for environmental monitoring...',
 'SOP', 'EFFECTIVE', 'PUBLIC',
 'Manufacturing', 'GMP,cleanroom,environmental',
 'QA Manager', 'Head of Quality',
 'SOP-ENV-MON-v2.0.pdf',
 '2024/01/DOC-SOP-202401-0001/effective-sop-env-mon.pdf',
 'application/pdf', 2457600,
 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
 '2024-01-15', '2026-01-15', '2025-01-15',
 TRUE, 47, FALSE, NOW(), 'SYSTEM'),

-- EXPIRING SOON
('DOC-SOP-202401-0003', '1.0', 1, 0,
 'Aseptic Gowning Procedure',
 'Personnel gowning procedure...',
 'SOP', 'EFFECTIVE', 'PUBLIC',
 'Manufacturing', 'gowning,aseptic',
 'Aseptic Specialist', 'QA Manager',
 'SOP-GOWNING-v1.0.pdf',
 '2024/01/DOC-SOP-202401-0003/sop-gowning-v1.pdf',
 'application/pdf', 1228800,
 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
 '2022-04-01',
 CURRENT_DATE + INTERVAL '15 days',
 CURRENT_DATE + INTERVAL '15 days',
 TRUE, 89, FALSE,
 NOW() - INTERVAL '730 days',
 'SYSTEM')

ON CONFLICT (doc_number, major_version, minor_version) DO NOTHING;


-- =============================================================
-- Approval seed (SAFE FK + EXISTENCE CHECK)
-- =============================================================
INSERT INTO dms_document_approvals
    (document_id, approver_id, approver_name, approver_role,
     decision, comments, decided_at, review_cycle, created_at)
SELECT
    d.id,
    u.id,
    'Dr. Sarah Patel',
    'QA Manager',
    'APPROVED',
    'Reviewed and approved',
    NOW() - INTERVAL '400 days',
    1,
    NOW() - INTERVAL '400 days'
FROM dms_documents d
JOIN users u ON u.username = 'admin'   -- SAFE user reference
WHERE d.doc_number = 'DOC-SOP-202401-0001'
  AND d.version = '2.0'
ON CONFLICT DO NOTHING;


-- =============================================================
-- Download logs seed (SAFE FK)
-- =============================================================
INSERT INTO dms_download_logs
    (document_id, doc_number, version, user_id, username,
     ip_address, downloaded_at, acknowledged, acknowledged_at)
SELECT
    d.id,
    d.doc_number,
    d.version,
    u.id,
    u.username,
    '192.168.1.101',
    NOW() - INTERVAL '10 days',
    TRUE,
    NOW() - INTERVAL '10 days'
FROM dms_documents d
JOIN users u ON u.username = 'admin'
WHERE d.doc_number = 'DOC-SOP-202401-0001'
  AND d.version = '2.0'
ON CONFLICT DO NOTHING;