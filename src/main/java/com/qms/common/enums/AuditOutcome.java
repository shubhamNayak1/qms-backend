package com.qms.common.enums;

/**
 * Outcome of the audited operation.
 * Allows filtering for failures without parsing action descriptions.
 */
public enum AuditOutcome {
    SUCCESS,    // operation completed normally
    FAILURE,    // operation was attempted but failed (exception thrown)
    PARTIAL,    // operation partially succeeded (e.g. bulk import with some failures)
    DENIED      // operation was rejected by security / business rules
}
