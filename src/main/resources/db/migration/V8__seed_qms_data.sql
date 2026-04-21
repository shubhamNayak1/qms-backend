-- =============================================================
-- V8__seed_qms_data.sql (PostgreSQL FIXED)
-- =============================================================

-- =============================================================
-- CAPA
-- =============================================================
INSERT INTO qms_capa
    (record_number, record_type, title, description, status, priority,
     assigned_to_name, raised_by_name, department, due_date,
     source, capa_type, is_deleted, created_at, created_by)
VALUES
    ('CAPA-202401-0001', 'CAPA',
     'Sterility Failure — Batch 2024-001',
     'Sterility testing returned positive.',
     'IN_PROGRESS', 'CRITICAL',
     'Dr. Sarah Patel', 'Lab Tech John', 'QA',
     CURRENT_DATE + INTERVAL '14 days',
     'Audit', 'Corrective',
     FALSE, NOW(), 'SYSTEM'),

    ('CAPA-202401-0003', 'CAPA',
     'Temperature Excursion',
     'Temperature exceeded limits.',
     'CLOSED', 'HIGH',
     'Facilities Team', 'Warehouse Supervisor', 'Warehouse',
     CURRENT_DATE - INTERVAL '7 days',
     'Internal', 'Corrective',
     FALSE, NOW() - INTERVAL '30 days', 'SYSTEM')

ON CONFLICT (record_number) DO NOTHING;


-- =============================================================
-- DEVIATION
-- =============================================================
INSERT INTO qms_deviation
    (record_number, record_type, title, description, status, priority,
     assigned_to_name, raised_by_name, department, due_date,
     deviation_type, product_batch, process_area,
     impact_assessment, capa_required, regulatory_reportable,
     is_deleted, created_at, created_by)
VALUES
    ('DEV-202401-0001', 'DEVIATION',
     'Mixing Time Exceeded',
     'Mixing exceeded spec.',
     'IN_PROGRESS', 'HIGH',
     'Process Engineer', 'Supervisor', 'Manufacturing',
     CURRENT_DATE + INTERVAL '10 days',
     'Unplanned', 'BATCH-101', 'Mixing',
     'Impact under review',
     TRUE, FALSE,
     FALSE, NOW(), 'SYSTEM')

ON CONFLICT (record_number) DO NOTHING;


-- =============================================================
-- INCIDENT
-- =============================================================
INSERT INTO qms_incident
    (record_number, record_type, title, description, status, priority,
     assigned_to_name, raised_by_name, department, due_date,
     incident_type, severity, location, occurrence_date,
     reported_by, immediate_action, injury_involved,
     is_deleted, created_at, created_by)
VALUES
    ('INC-202401-0001', 'INCIDENT',
     'Slip and Fall',
     'Operator slipped.',
     'IN_PROGRESS', 'HIGH',
     'EHS Manager', 'First Aider', 'Safety',
     CURRENT_DATE + INTERVAL '5 days',
     'Safety', 'Major', 'Corridor B',
     CURRENT_DATE,
     'Operator',
     'Area secured',
     TRUE,
     FALSE, NOW(), 'SYSTEM')

ON CONFLICT (record_number) DO NOTHING;


-- =============================================================
-- CHANGE CONTROL
-- =============================================================
INSERT INTO qms_change_control
    (record_number, record_type, title, description, status, priority,
     assigned_to_name, raised_by_name, department, due_date,
     change_type, change_reason, risk_level, validation_required,
     is_deleted, created_at, created_by)
VALUES
    ('CC-202401-0001', 'CHANGE_CONTROL',
     'Replace Equipment',
     'Granulator replacement.',
     'PENDING_APPROVAL', 'HIGH',
     'Validation Lead', 'Maintenance Manager', 'Engineering',
     CURRENT_DATE + INTERVAL '45 days',
     'Equipment', 'End-of-life',
     'Medium', TRUE,
     FALSE, NOW(), 'SYSTEM')

ON CONFLICT (record_number) DO NOTHING;


-- =============================================================
-- MARKET COMPLAINT
-- =============================================================
INSERT INTO qms_market_complaint
    (record_number, record_type, title, description, status, priority,
     assigned_to_name, raised_by_name, department, due_date,
     customer_name, customer_country, product_name, batch_number,
     complaint_category, complaint_source, received_date,
     reportable_to_authority, is_deleted, created_at, created_by)
VALUES
    ('MC-202401-0001', 'MARKET_COMPLAINT',
     'Foreign Particle',
     'Particle found in product.',
     'IN_PROGRESS', 'CRITICAL',
     'QA Manager', 'Customer Service', 'QA',
     CURRENT_DATE + INTERVAL '10 days',
     'Distributor GmbH', 'Germany', 'Alpha 500mg', 'BATCH-042',
     'Quality', 'Email',
     CURRENT_DATE,
     TRUE,
     FALSE, NOW(), 'SYSTEM')

ON CONFLICT (record_number) DO NOTHING;