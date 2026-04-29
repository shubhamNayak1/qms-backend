-- V14: Dynamic saved reports system

CREATE TABLE saved_reports (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL,
    description          TEXT,
    module               VARCHAR(50)  NOT NULL,
    format               VARCHAR(10)  NOT NULL DEFAULT 'EXCEL',
    date_from            DATE,
    date_to              DATE,
    dimensions           TEXT,
    metrics              TEXT,
    extra_filters        TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    file_path            TEXT,
    file_name            VARCHAR(255),
    file_size_bytes      BIGINT,
    last_run_at          TIMESTAMP,
    run_count            INTEGER      NOT NULL DEFAULT 0,
    last_run_error       TEXT,
    is_disabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by_user_id   BIGINT,
    created_by_username  VARCHAR(100),
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE report_run_history (
    id                      BIGSERIAL PRIMARY KEY,
    report_id               BIGINT NOT NULL REFERENCES saved_reports(id),
    triggered_by_user_id    BIGINT,
    triggered_by_username   VARCHAR(100),
    status                  VARCHAR(20) NOT NULL,
    started_at              TIMESTAMP   NOT NULL,
    completed_at            TIMESTAMP,
    duration_ms             BIGINT,
    row_count               BIGINT,
    file_path               TEXT,
    file_size_bytes         BIGINT,
    error_message           TEXT,
    created_at              TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saved_reports_module     ON saved_reports(module);
CREATE INDEX idx_saved_reports_created_by ON saved_reports(created_by_user_id);
CREATE INDEX idx_saved_reports_status     ON saved_reports(status);
CREATE INDEX idx_report_run_history_rid   ON report_run_history(report_id);
