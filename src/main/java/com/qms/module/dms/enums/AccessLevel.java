package com.qms.module.dms.enums;

/**
 * Who can access a document.
 * Evaluated in addition to role-based access control.
 */
public enum AccessLevel {
    PUBLIC,         // any authenticated user
    RESTRICTED,     // specific roles/departments only (enforced by DocumentAccessService)
    CONFIDENTIAL,   // QA Manager and above only
    TOP_SECRET      // Super Admin and specific named users only
}
