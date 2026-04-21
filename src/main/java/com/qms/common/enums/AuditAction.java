package com.qms.common.enums;

/**
 * Standardised action codes stored in audit_logs.action.
 *
 * Naming convention:  VERB  (what happened)
 * The entity / module tells you to WHAT it happened.
 *
 * Extend this enum as new operations are introduced — do NOT use free-text strings
 * in code; always refer to this enum so audit logs remain consistently queryable.
 */
public enum AuditAction {

    // ── Identity / Auth ────────────────────────────────────
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    TOKEN_REFRESHED,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,

    // ── CRUD ───────────────────────────────────────────────
    CREATE,
    READ,           // use sparingly — only for sensitive records (e.g. document download)
    UPDATE,
    DELETE,
    RESTORE,        // undo soft-delete

    // ── Workflow transitions ───────────────────────────────
    SUBMIT,
    APPROVE,
    REJECT,
    CLOSE,
    REOPEN,
    CANCEL,
    ARCHIVE,
    PUBLISH,
    OBSOLETE,

    // ── Role / Permission ──────────────────────────────────
    ROLE_ASSIGNED,
    ROLE_REMOVED,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,

    // ── File / Document ────────────────────────────────────
    UPLOAD,
    DOWNLOAD,
    NEW_VERSION,

    // ── Training ───────────────────────────────────────────
    TRAINING_ASSIGNED,
    TRAINING_STARTED,
    TRAINING_COMPLETED,
    TRAINING_FAILED,

    // ── Data export ────────────────────────────────────────
    EXPORT,

    // ── System ─────────────────────────────────────────────
    SYSTEM_EVENT,
    BATCH_IMPORT,
    CONFIG_CHANGED
}
