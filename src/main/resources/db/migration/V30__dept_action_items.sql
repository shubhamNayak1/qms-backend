-- Round-N (2026-07-04) tester CC-Point-2 · Issue 6
-- Each department can now attach MULTIPLE action items with individual
-- target dates to a single dept-comment row. Previously a dept-comment
-- carried one comment + one optional target_date on the parent row.
-- Action items become the tracked unit — each has its own status +
-- target date + who completed it + when.
--
-- The existing qms_department_comments columns (comment, action_required,
-- target_date) are preserved for backwards compatibility; the dept's
-- overall feedback stays there, while the atomic action-items live in
-- this child table.
CREATE TABLE IF NOT EXISTS qms_department_action_items (
    id                BIGSERIAL PRIMARY KEY,
    dept_comment_id   BIGINT NOT NULL REFERENCES qms_department_comments(id),
    description       TEXT NOT NULL,
    target_date       DATE,
    status            VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    completed_at      TIMESTAMP,
    completed_by_id   BIGINT,
    completed_by_name VARCHAR(160),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,
    is_deleted        BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_qms_dai_dept_comment ON qms_department_action_items(dept_comment_id);
CREATE INDEX IF NOT EXISTS idx_qms_dai_target_date  ON qms_department_action_items(target_date);
CREATE INDEX IF NOT EXISTS idx_qms_dai_status       ON qms_department_action_items(status);
