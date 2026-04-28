-- ============================================================
-- V12 — LMS Redesign: New unified training flow
--       CREATE → REVIEW → PLAN → ALLOCATE → TRAIN → COMPLIANCE → RESULT
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 1. Extend lms_programs
-- ─────────────────────────────────────────────────────────────

ALTER TABLE lms_programs
    ADD COLUMN IF NOT EXISTS training_type       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS training_sub_type   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS departments         VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS trainer_id          BIGINT,
    ADD COLUMN IF NOT EXISTS trainer_name        VARCHAR(150),
    ADD COLUMN IF NOT EXISTS vendor_name         VARCHAR(150),
    ADD COLUMN IF NOT EXISTS coordinator_id      BIGINT,
    ADD COLUMN IF NOT EXISTS coordinator_name    VARCHAR(150),
    ADD COLUMN IF NOT EXISTS location            VARCHAR(300),
    ADD COLUMN IF NOT EXISTS conference_link     VARCHAR(500),
    ADD COLUMN IF NOT EXISTS exam_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS rejection_reason    TEXT;

-- Back-fill: map old ACTIVE status — no change needed
-- Back-fill: set exam_enabled = assessment_required for existing rows
UPDATE lms_programs SET exam_enabled = assessment_required WHERE exam_enabled = FALSE;

-- ─────────────────────────────────────────────────────────────
-- 2. Extend lms_enrollments
-- ─────────────────────────────────────────────────────────────

ALTER TABLE lms_enrollments
    ADD COLUMN IF NOT EXISTS attendance_marked              BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS attendance_date                DATE,
    ADD COLUMN IF NOT EXISTS compliance_submitted_at        TIMESTAMP,
    ADD COLUMN IF NOT EXISTS compliance_reviewed_at         TIMESTAMP,
    ADD COLUMN IF NOT EXISTS compliance_reviewed_by         VARCHAR(150),
    ADD COLUMN IF NOT EXISTS retraining_of_enrollment_id   BIGINT;

-- Back-fill: rename ENROLLED → ALLOCATED for existing data
-- (keep ENROLLED as valid in enum for backward compat, but new rows use ALLOCATED)
-- No DB-level rename needed since it's stored as string.

-- ─────────────────────────────────────────────────────────────
-- 3. lms_training_sessions
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS lms_training_sessions (
    id                   BIGSERIAL PRIMARY KEY,
    program_id           BIGINT NOT NULL REFERENCES lms_programs(id),
    session_date         DATE   NOT NULL,
    session_end_date     DATE,
    start_time           TIME,
    end_time             TIME,
    venue                VARCHAR(300),
    meeting_link         VARCHAR(500),
    trainer_id           BIGINT,
    trainer_name         VARCHAR(150),
    coordinator_id       BIGINT,
    coordinator_name     VARCHAR(150),
    max_participants     INT,
    status               VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    cancellation_reason  TEXT,
    notes                TEXT,
    created_by           VARCHAR(100),
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_program ON lms_training_sessions(program_id);
CREATE INDEX IF NOT EXISTS idx_session_date    ON lms_training_sessions(session_date);
CREATE INDEX IF NOT EXISTS idx_session_status  ON lms_training_sessions(status);

-- ─────────────────────────────────────────────────────────────
-- 4. lms_training_attendance
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS lms_training_attendance (
    id               BIGSERIAL PRIMARY KEY,
    session_id       BIGINT  NOT NULL REFERENCES lms_training_sessions(id),
    enrollment_id    BIGINT  NOT NULL,
    user_id          BIGINT  NOT NULL,
    user_name        VARCHAR(150),
    is_present       BOOLEAN NOT NULL DEFAULT FALSE,
    attendance_date  DATE,
    notes            TEXT,
    marked_by        VARCHAR(150),
    marked_at        TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_attendance_session_enrollment UNIQUE (session_id, enrollment_id)
);

CREATE INDEX IF NOT EXISTS idx_att_session    ON lms_training_attendance(session_id);
CREATE INDEX IF NOT EXISTS idx_att_enrollment ON lms_training_attendance(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_att_user       ON lms_training_attendance(user_id);

-- ─────────────────────────────────────────────────────────────
-- 5. lms_compliance_submissions
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS lms_compliance_submissions (
    id                        BIGSERIAL PRIMARY KEY,
    enrollment_id             BIGINT      NOT NULL UNIQUE REFERENCES lms_enrollments(id),
    attachment_storage_key    VARCHAR(500),
    attachment_file_name      VARCHAR(255),
    attachment_file_size_bytes BIGINT,
    qna_answers               TEXT,
    status                    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason          TEXT,
    submitted_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_compsub_enrollment ON lms_compliance_submissions(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_compsub_status     ON lms_compliance_submissions(status);

-- ─────────────────────────────────────────────────────────────
-- 6. lms_compliance_reviews
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS lms_compliance_reviews (
    id             BIGSERIAL PRIMARY KEY,
    submission_id  BIGINT      NOT NULL UNIQUE REFERENCES lms_compliance_submissions(id),
    reviewer_id    BIGINT,
    reviewer_name  VARCHAR(150),
    reviewer_role  VARCHAR(50),
    decision       VARCHAR(20) NOT NULL,
    comments       TEXT,
    reviewed_at    TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comprev_submission ON lms_compliance_reviews(submission_id);

-- ─────────────────────────────────────────────────────────────
-- 7. lms_training_needs (TNI)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS lms_training_needs (
    id                     BIGSERIAL PRIMARY KEY,
    enrollment_id          BIGINT      NOT NULL,
    user_id                BIGINT      NOT NULL,
    user_name              VARCHAR(150),
    department             VARCHAR(100),
    designation            VARCHAR(150),
    job_description        TEXT,
    identified_gaps        TEXT,
    recommended_trainings  TEXT,
    notes                  TEXT,
    generated_by           VARCHAR(150),
    generated_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tni_enrollment ON lms_training_needs(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_tni_user       ON lms_training_needs(user_id);
