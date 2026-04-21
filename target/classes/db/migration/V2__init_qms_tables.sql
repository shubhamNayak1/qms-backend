-- =============================================================
-- V2__init_qms_tables.sql (PostgreSQL FIXED & OPTIMIZED)
-- =============================================================

-- =============================================================
-- 1. CAPA
-- =============================================================
CREATE TABLE IF NOT EXISTS qms_capa (
    id BIGSERIAL PRIMARY KEY,
    record_number VARCHAR(30) NOT NULL UNIQUE,
    record_type VARCHAR(30) NOT NULL DEFAULT 'CAPA',

    title VARCHAR(255) NOT NULL,
    description TEXT,

    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    assigned_to_id BIGINT REFERENCES users(id),
    assigned_to_name VARCHAR(150),
    raised_by_id BIGINT REFERENCES users(id),
    raised_by_name VARCHAR(150),
    department VARCHAR(100),

    due_date DATE,
    closed_date DATE,
    target_completion_date DATE,

    approved_by_id BIGINT REFERENCES users(id),
    approved_by_name VARCHAR(150),
    approved_at TIMESTAMP,
    approval_comments VARCHAR(1000),

    root_cause TEXT,
    corrective_action TEXT,
    comments TEXT,
    status_history TEXT,

    source VARCHAR(100),
    capa_type VARCHAR(80),
    preventive_action TEXT,
    effectiveness_check_date DATE,
    effectiveness_result TEXT,
    is_effective BOOLEAN,
    linked_deviation_number VARCHAR(30),

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_capa_status ON qms_capa(status);
CREATE INDEX IF NOT EXISTS idx_capa_priority ON qms_capa(priority);
CREATE INDEX IF NOT EXISTS idx_capa_assigned ON qms_capa(assigned_to_id);
CREATE INDEX IF NOT EXISTS idx_capa_due ON qms_capa(due_date);
CREATE INDEX IF NOT EXISTS idx_capa_deleted ON qms_capa(is_deleted);
CREATE INDEX IF NOT EXISTS idx_capa_dept ON qms_capa(department);
CREATE INDEX IF NOT EXISTS idx_capa_source ON qms_capa(source);
CREATE INDEX IF NOT EXISTS idx_capa_status_deleted ON qms_capa(status, is_deleted);


-- =============================================================
-- 2. DEVIATION
-- =============================================================
CREATE TABLE IF NOT EXISTS qms_deviation (
    id BIGSERIAL PRIMARY KEY,
    record_number VARCHAR(30) NOT NULL UNIQUE,
    record_type VARCHAR(30) NOT NULL DEFAULT 'DEVIATION',

    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    assigned_to_id BIGINT REFERENCES users(id),
    assigned_to_name VARCHAR(150),
    raised_by_id BIGINT REFERENCES users(id),
    raised_by_name VARCHAR(150),
    department VARCHAR(100),

    due_date DATE,
    closed_date DATE,
    target_completion_date DATE,

    approved_by_id BIGINT REFERENCES users(id),
    approved_by_name VARCHAR(150),
    approved_at TIMESTAMP,
    approval_comments VARCHAR(1000),

    root_cause TEXT,
    corrective_action TEXT,
    comments TEXT,
    status_history TEXT,

    deviation_type VARCHAR(80),
    product_batch VARCHAR(100),
    process_area VARCHAR(100),
    impact_assessment TEXT,
    capa_required BOOLEAN DEFAULT FALSE,
    capa_reference VARCHAR(30),
    regulatory_reportable BOOLEAN DEFAULT FALSE,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_dev_status ON qms_deviation(status);
CREATE INDEX IF NOT EXISTS idx_dev_priority ON qms_deviation(priority);
CREATE INDEX IF NOT EXISTS idx_dev_assigned ON qms_deviation(assigned_to_id);
CREATE INDEX IF NOT EXISTS idx_dev_due ON qms_deviation(due_date);
CREATE INDEX IF NOT EXISTS idx_dev_deleted ON qms_deviation(is_deleted);
CREATE INDEX IF NOT EXISTS idx_dev_status_deleted ON qms_deviation(status, is_deleted);


-- =============================================================
-- 3. INCIDENT
-- =============================================================
CREATE TABLE IF NOT EXISTS qms_incident (
    id BIGSERIAL PRIMARY KEY,
    record_number VARCHAR(30) NOT NULL UNIQUE,
    record_type VARCHAR(30) NOT NULL DEFAULT 'INCIDENT',

    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    assigned_to_id BIGINT REFERENCES users(id),
    assigned_to_name VARCHAR(150),
    raised_by_id BIGINT REFERENCES users(id),
    raised_by_name VARCHAR(150),
    department VARCHAR(100),

    due_date DATE,
    closed_date DATE,
    target_completion_date DATE,

    approved_by_id BIGINT REFERENCES users(id),
    approved_by_name VARCHAR(150),
    approved_at TIMESTAMP,
    approval_comments VARCHAR(1000),

    root_cause TEXT,
    corrective_action TEXT,
    comments TEXT,
    status_history TEXT,

    incident_type VARCHAR(80),
    severity VARCHAR(30),
    location VARCHAR(150),
    occurrence_date DATE,
    reported_by VARCHAR(150),
    immediate_action TEXT,
    investigation_details TEXT,
    capa_reference VARCHAR(30),
    injury_involved BOOLEAN DEFAULT FALSE,
    injury_details TEXT,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_inc_status ON qms_incident(status);
CREATE INDEX IF NOT EXISTS idx_inc_priority ON qms_incident(priority);
CREATE INDEX IF NOT EXISTS idx_inc_assigned ON qms_incident(assigned_to_id);
CREATE INDEX IF NOT EXISTS idx_inc_severity ON qms_incident(severity);
CREATE INDEX IF NOT EXISTS idx_inc_due ON qms_incident(due_date);
CREATE INDEX IF NOT EXISTS idx_inc_deleted ON qms_incident(is_deleted);
CREATE INDEX IF NOT EXISTS idx_inc_status_deleted ON qms_incident(status, is_deleted);


-- =============================================================
-- 4. CHANGE CONTROL
-- =============================================================
CREATE TABLE IF NOT EXISTS qms_change_control (
    id BIGSERIAL PRIMARY KEY,
    record_number VARCHAR(30) NOT NULL UNIQUE,
    record_type VARCHAR(30) NOT NULL DEFAULT 'CHANGE_CONTROL',

    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    assigned_to_id BIGINT REFERENCES users(id),
    assigned_to_name VARCHAR(150),
    raised_by_id BIGINT REFERENCES users(id),
    raised_by_name VARCHAR(150),
    department VARCHAR(100),

    due_date DATE,
    closed_date DATE,
    target_completion_date DATE,

    approved_by_id BIGINT REFERENCES users(id),
    approved_by_name VARCHAR(150),
    approved_at TIMESTAMP,
    approval_comments VARCHAR(1000),

    root_cause TEXT,
    corrective_action TEXT,
    comments TEXT,
    status_history TEXT,

    change_type VARCHAR(80),
    change_reason TEXT,
    risk_level VARCHAR(20),
    risk_assessment TEXT,
    implementation_plan TEXT,
    implementation_date DATE,
    validation_required BOOLEAN DEFAULT FALSE,
    validation_details TEXT,
    validation_completion_date DATE,
    regulatory_submission_required BOOLEAN DEFAULT FALSE,
    regulatory_submission_reference VARCHAR(100),
    rollback_plan TEXT,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_cc_status ON qms_change_control(status);
CREATE INDEX IF NOT EXISTS idx_cc_priority ON qms_change_control(priority);
CREATE INDEX IF NOT EXISTS idx_cc_assigned ON qms_change_control(assigned_to_id);
CREATE INDEX IF NOT EXISTS idx_cc_type ON qms_change_control(change_type);
CREATE INDEX IF NOT EXISTS idx_cc_due ON qms_change_control(due_date);
CREATE INDEX IF NOT EXISTS idx_cc_deleted ON qms_change_control(is_deleted);
CREATE INDEX IF NOT EXISTS idx_cc_status_deleted ON qms_change_control(status, is_deleted);


-- =============================================================
-- 5. MARKET COMPLAINT
-- =============================================================
CREATE TABLE IF NOT EXISTS qms_market_complaint (
    id BIGSERIAL PRIMARY KEY,
    record_number VARCHAR(30) NOT NULL UNIQUE,
    record_type VARCHAR(30) NOT NULL DEFAULT 'MARKET_COMPLAINT',

    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    assigned_to_id BIGINT REFERENCES users(id),
    assigned_to_name VARCHAR(150),
    raised_by_id BIGINT REFERENCES users(id),
    raised_by_name VARCHAR(150),
    department VARCHAR(100),

    due_date DATE,
    closed_date DATE,
    target_completion_date DATE,

    approved_by_id BIGINT REFERENCES users(id),
    approved_by_name VARCHAR(150),
    approved_at TIMESTAMP,
    approval_comments VARCHAR(1000),

    root_cause TEXT,
    corrective_action TEXT,
    comments TEXT,
    status_history TEXT,

    customer_name VARCHAR(150),
    customer_contact VARCHAR(200),
    customer_country VARCHAR(80),
    product_name VARCHAR(150),
    batch_number VARCHAR(80),
    expiry_date DATE,
    complaint_category VARCHAR(80),
    complaint_source VARCHAR(80),
    received_date DATE,
    reportable_to_authority BOOLEAN DEFAULT FALSE,
    authority_report_reference VARCHAR(100),
    authority_report_date DATE,
    resolution_details TEXT,
    customer_response TEXT,
    customer_notified_date DATE,
    customer_satisfied BOOLEAN,
    capa_reference VARCHAR(30),
    sample_returned BOOLEAN DEFAULT FALSE,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_mc_status ON qms_market_complaint(status);
CREATE INDEX IF NOT EXISTS idx_mc_priority ON qms_market_complaint(priority);
CREATE INDEX IF NOT EXISTS idx_mc_assigned ON qms_market_complaint(assigned_to_id);
CREATE INDEX IF NOT EXISTS idx_mc_category ON qms_market_complaint(complaint_category);
CREATE INDEX IF NOT EXISTS idx_mc_product ON qms_market_complaint(product_name);
CREATE INDEX IF NOT EXISTS idx_mc_reportable ON qms_market_complaint(reportable_to_authority);
CREATE INDEX IF NOT EXISTS idx_mc_due ON qms_market_complaint(due_date);
CREATE INDEX IF NOT EXISTS idx_mc_deleted ON qms_market_complaint(is_deleted);
CREATE INDEX IF NOT EXISTS idx_mc_status_deleted ON qms_market_complaint(status, is_deleted);