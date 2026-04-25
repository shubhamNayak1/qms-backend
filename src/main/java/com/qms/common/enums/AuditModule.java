package com.qms.common.enums;

/**
 * Identifies which application module produced the audit event.
 * Used for filtering and dashboard grouping in the Reports module.
 */
public enum AuditModule {
    AUTH,
    USER,
    ROLE,
    PERMISSION,
    CAPA,
    DEVIATION,
    INCIDENT,
    CHANGE_CONTROL,
    MARKET_COMPLAINT,
    DOCUMENT,
    COURSE,
    TRAINING,
    REPORT,
    PASSWORD_POLICY,
    SYSTEM
}
