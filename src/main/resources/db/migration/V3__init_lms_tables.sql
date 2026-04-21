-- =============================================================
-- V3__init_lms_tables.sql (PostgreSQL FIXED)
-- =============================================================

-- =============================================================
-- 1. Training Programs
-- =============================================================
CREATE TABLE IF NOT EXISTS lms_programs (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    department VARCHAR(100),
    tags VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    is_mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    estimated_duration_minutes INT,
    certificate_validity_years INT,
    completion_deadline_days INT,
    assessment_required BOOLEAN NOT NULL DEFAULT FALSE,
    pass_score INT NOT NULL DEFAULT 80,
    max_attempts INT NOT NULL DEFAULT 3,
    created_by_name VARCHAR(150),
    owner_id BIGINT REFERENCES users(id),
    owner_name VARCHAR(150),

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_prog_status ON lms_programs(status);
CREATE INDEX IF NOT EXISTS idx_prog_category ON lms_programs(category);
CREATE INDEX IF NOT EXISTS idx_prog_department ON lms_programs(department);
CREATE INDEX IF NOT EXISTS idx_prog_deleted ON lms_programs(is_deleted);
CREATE INDEX IF NOT EXISTS idx_prog_mandatory ON lms_programs(is_mandatory);


-- =============================================================
-- 2. Program Content Items
-- =============================================================
CREATE TABLE IF NOT EXISTS lms_program_contents (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    display_order INT NOT NULL DEFAULT 1,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    duration_minutes INT,

    dms_document_id BIGINT,
    dms_doc_number VARCHAR(40),
    dms_doc_version VARCHAR(10),
    content_url VARCHAR(1000),
    inline_content TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,

    CONSTRAINT fk_content_program FOREIGN KEY (program_id)
        REFERENCES lms_programs (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_content_program ON lms_program_contents(program_id);
CREATE INDEX IF NOT EXISTS idx_content_type ON lms_program_contents(content_type);
CREATE INDEX IF NOT EXISTS idx_content_dms ON lms_program_contents(dms_document_id);


-- =============================================================
-- 3. Program DMS Links
-- =============================================================
CREATE TABLE IF NOT EXISTS lms_program_document_links (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL,
    dms_document_id BIGINT NOT NULL,
    dms_doc_number VARCHAR(40) NOT NULL,
    dms_doc_version VARCHAR(10) NOT NULL,
    dms_doc_title VARCHAR(300),
    trigger_review_on_update BOOLEAN NOT NULL DEFAULT TRUE,
    linked_by VARCHAR(100),
    linked_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_pdl_program FOREIGN KEY (program_id)
        REFERENCES lms_programs (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pdl_program ON lms_program_document_links(program_id);
CREATE INDEX IF NOT EXISTS idx_pdl_doc_id ON lms_program_document_links(dms_document_id);
CREATE INDEX IF NOT EXISTS idx_pdl_doc_num ON lms_program_document_links(dms_doc_number);


-- =============================================================
-- 4. Assessments
-- =============================================================
CREATE TABLE IF NOT EXISTS lms_assessments (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    instructions TEXT,
    time_limit_minutes INT,
    pass_score INT NOT NULL DEFAULT 80,
    randomise_questions BOOLEAN NOT NULL DEFAULT FALSE,
    randomise_answers BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,

    CONSTRAINT fk_assessment_program FOREIGN KEY (program_id)
        REFERENCES lms_programs (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assessment_program ON lms_assessments(program_id);


-- =============================================================
-- 5. Questions
-- =============================================================
CREATE TABLE IF NOT EXISTS lms_assessment_questions (
    id BIGSERIAL PRIMARY KEY,
    assessment_id BIGINT NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    question_text TEXT NOT NULL,
    options TEXT,
    correct_answer TEXT,
    explanation TEXT,
    marks INT NOT NULL DEFAULT 1,
    display_order INT NOT NULL DEFAULT 1,

    CONSTRAINT fk_aq_assessment FOREIGN KEY (assessment_id)
        REFERENCES lms_assessments (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_aq_assessment ON lms_assessment_questions(assessment_id);


-- =============================================================
-- 6. Enrollments
-- =============================================================
CREATE TABLE IF NOT EXISTS lms_enrollments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    user_name VARCHAR(150),
    user_email VARCHAR(200),
    user_department VARCHAR(100),
    program_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENROLLED',
    due_date DATE,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    progress_percent INT NOT NULL DEFAULT 0,
    attempts_used INT NOT NULL DEFAULT 0,
    last_score INT,
    assigned_by_id BIGINT REFERENCES users(id),
    assigned_by_name VARCHAR(150),
    assignment_reason VARCHAR(500),
    waiver_reason VARCHAR(500),
    waived_by_name VARCHAR(150),
    waived_at TIMESTAMP,

    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT uk_enrollment_user_program UNIQUE (user_id, program_id),
    CONSTRAINT fk_enrollment_program FOREIGN KEY (program_id)
        REFERENCES lms_programs (id)
);

CREATE INDEX IF NOT EXISTS idx_enroll_user ON lms_enrollments(user_id);
CREATE INDEX IF NOT EXISTS idx_enroll_program ON lms_enrollments(program_id);
CREATE INDEX IF NOT EXISTS idx_enroll_status ON lms_enrollments(status);
CREATE INDEX IF NOT EXISTS idx_enroll_deadline ON lms_enrollments(due_date);
CREATE INDEX IF NOT EXISTS idx_enroll_deleted ON lms_enrollments(is_deleted);
CREATE INDEX IF NOT EXISTS idx_enroll_status_deleted ON lms_enrollments(status, is_deleted);


-- =============================================================
-- 7. Content Progress
-- =============================================================
CREATE TABLE IF NOT EXISTS lms_content_progress (
    id BIGSERIAL PRIMARY KEY,
    enrollment_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    view_percent INT NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    first_accessed_at TIMESTAMP,
    last_accessed_at TIMESTAMP,
    completed_at TIMESTAMP,
    time_spent_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_cp_enrollment_content UNIQUE (enrollment_id, content_id),
    CONSTRAINT fk_cp_enrollment FOREIGN KEY (enrollment_id)
        REFERENCES lms_enrollments (id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_content FOREIGN KEY (content_id)
        REFERENCES lms_program_contents (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cp_enrollment ON lms_content_progress(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_cp_content ON lms_content_progress(content_id);
CREATE INDEX IF NOT EXISTS idx_cp_completed ON lms_content_progress(completed_at);


-- =============================================================
-- 8. Assessment Attempts
-- =============================================================
CREATE TABLE IF NOT EXISTS lms_assessment_attempts (
    id BIGSERIAL PRIMARY KEY,
    enrollment_id BIGINT NOT NULL,
    assessment_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    raw_score INT,
    total_marks INT,
    score_percent INT,
    passed BOOLEAN,
    answers_json TEXT,
    reviewer_comments VARCHAR(1000),
    started_at TIMESTAMP,
    submitted_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_attempt_enrollment FOREIGN KEY (enrollment_id)
        REFERENCES lms_enrollments (id) ON DELETE CASCADE,
    CONSTRAINT fk_attempt_assessment FOREIGN KEY (assessment_id)
        REFERENCES lms_assessments (id)
);

CREATE INDEX IF NOT EXISTS idx_attempt_enrollment ON lms_assessment_attempts(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_attempt_assessment ON lms_assessment_attempts(assessment_id);
CREATE INDEX IF NOT EXISTS idx_attempt_status ON lms_assessment_attempts(status);


-- =============================================================
-- 9. Certificates
-- =============================================================
CREATE TABLE IF NOT EXISTS lms_certificates (
    id BIGSERIAL PRIMARY KEY,
    enrollment_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    user_name VARCHAR(150),
    program_id BIGINT NOT NULL,
    program_title VARCHAR(255),
    program_code VARCHAR(30),
    certificate_number VARCHAR(60) NOT NULL UNIQUE,
    issuer VARCHAR(200),
    issued_date DATE NOT NULL,
    expiry_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    score_achieved INT,
    revoked_reason VARCHAR(500),
    revoked_by VARCHAR(100),
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,

    CONSTRAINT fk_cert_enrollment FOREIGN KEY (enrollment_id)
        REFERENCES lms_enrollments (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cert_enrollment ON lms_certificates(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_cert_user ON lms_certificates(user_id);
CREATE INDEX IF NOT EXISTS idx_cert_program ON lms_certificates(program_id);
CREATE INDEX IF NOT EXISTS idx_cert_expiry ON lms_certificates(expiry_date);
CREATE INDEX IF NOT EXISTS idx_cert_status ON lms_certificates(status);