-- =============================================================
-- V4__init_dms_tables.sql (PostgreSQL FIXED)
-- =============================================================


-- =============================================================
-- 1. Document metadata
-- =============================================================
CREATE TABLE IF NOT EXISTS dms_documents (

    id BIGSERIAL PRIMARY KEY,

    doc_number VARCHAR(40) NOT NULL,
    version VARCHAR(10) NOT NULL,

    major_version INT NOT NULL DEFAULT 1,
    minor_version INT NOT NULL DEFAULT 0,

    title VARCHAR(300) NOT NULL,
    description TEXT,

    category VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    access_level VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',

    department VARCHAR(100),
    tags VARCHAR(500),

    owner_id BIGINT REFERENCES users(id),
    owner_name VARCHAR(150),
    author_id BIGINT REFERENCES users(id),
    author_name VARCHAR(150),

    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(1000) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    sha256_checksum VARCHAR(64),

    effective_date DATE,
    expiry_date DATE,
    review_date DATE,
    published_at TIMESTAMP,
    obsoleted_at TIMESTAMP,

    approved_by_id BIGINT REFERENCES users(id),
    approved_by_name VARCHAR(150),
    approved_at TIMESTAMP,
    approval_comments VARCHAR(1000),
    rejection_reason VARCHAR(1000),

    parent_id BIGINT,
    change_summary TEXT,
    is_controlled BOOLEAN NOT NULL DEFAULT TRUE,
    download_count BIGINT NOT NULL DEFAULT 0,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT fk_doc_parent FOREIGN KEY (parent_id)
        REFERENCES dms_documents (id) ON DELETE SET NULL,

    -- Prevent duplicate versions of same doc
    CONSTRAINT uq_doc_version UNIQUE (doc_number, major_version, minor_version)
);

-- Indexes (Flyway safe)
CREATE INDEX IF NOT EXISTS idx_doc_number ON dms_documents(doc_number);
CREATE INDEX IF NOT EXISTS idx_doc_status ON dms_documents(status);
CREATE INDEX IF NOT EXISTS idx_doc_category ON dms_documents(category);
CREATE INDEX IF NOT EXISTS idx_doc_department ON dms_documents(department);
CREATE INDEX IF NOT EXISTS idx_doc_effective ON dms_documents(effective_date);
CREATE INDEX IF NOT EXISTS idx_doc_expiry ON dms_documents(expiry_date);
CREATE INDEX IF NOT EXISTS idx_doc_review ON dms_documents(review_date);
CREATE INDEX IF NOT EXISTS idx_doc_parent ON dms_documents(parent_id);
CREATE INDEX IF NOT EXISTS idx_doc_deleted ON dms_documents(is_deleted);
CREATE INDEX IF NOT EXISTS idx_doc_owner ON dms_documents(owner_id);
CREATE INDEX IF NOT EXISTS idx_doc_status_deleted ON dms_documents(status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_doc_number_version ON dms_documents(doc_number, major_version, minor_version);
CREATE INDEX IF NOT EXISTS idx_doc_expiry_status ON dms_documents(expiry_date, status, is_deleted);


-- =============================================================
-- 2. Document Approvals
-- =============================================================
CREATE TABLE IF NOT EXISTS dms_document_approvals (

    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,

    approver_id BIGINT NOT NULL REFERENCES users(id),
    approver_name VARCHAR(150) NOT NULL,
    approver_role VARCHAR(80),

    decision VARCHAR(20) NOT NULL DEFAULT 'UNDER_REVIEW',
    comments VARCHAR(1000),
    decided_at TIMESTAMP,
    review_cycle INT NOT NULL DEFAULT 1,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_approval_document FOREIGN KEY (document_id)
        REFERENCES dms_documents (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_approval_document ON dms_document_approvals(document_id);
CREATE INDEX IF NOT EXISTS idx_approval_approver ON dms_document_approvals(approver_id);
CREATE INDEX IF NOT EXISTS idx_approval_decision ON dms_document_approvals(decision);
CREATE INDEX IF NOT EXISTS idx_approval_cycle ON dms_document_approvals(document_id, review_cycle);


-- =============================================================
-- 3. Download Logs
-- =============================================================
CREATE TABLE IF NOT EXISTS dms_download_logs (

    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,

    doc_number VARCHAR(40) NOT NULL,
    version VARCHAR(10) NOT NULL,

    user_id BIGINT REFERENCES users(id),
    username VARCHAR(100),

    ip_address VARCHAR(60),
    user_agent VARCHAR(500),
    downloaded_at TIMESTAMP NOT NULL DEFAULT NOW(),

    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_at TIMESTAMP,

    CONSTRAINT fk_dl_document FOREIGN KEY (document_id)
        REFERENCES dms_documents (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dl_document ON dms_download_logs(document_id);
CREATE INDEX IF NOT EXISTS idx_dl_user ON dms_download_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_dl_timestamp ON dms_download_logs(downloaded_at);
CREATE INDEX IF NOT EXISTS idx_dl_ack ON dms_download_logs(document_id, acknowledged);