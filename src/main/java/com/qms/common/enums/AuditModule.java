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
    QMS_AUDIT,
    PASSWORD_POLICY,

    /** Site / Department / org-tree mutations. */
    ORG,
    /** License generate / assign / revoke. */
    LICENSE,
    /**
     * Cross-module QMS operations that aren't tied to one specific
     * sub-module — line items, department comments, target-date
     * extensions. The audit row's entityType / entityId stays specific
     * (e.g. "QmsLineItem", "QmsDepartmentComment") so reports can still
     * drill in.
     */
    QMS,

    SYSTEM
}
