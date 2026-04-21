package com.qms.module.lms.enums;

/**
 * Lifecycle of one user's enrollment in a TrainingProgram.
 *
 *  ENROLLED → IN_PROGRESS → COMPLETED (passed)
 *                         → FAILED    (below pass mark)
 *  FAILED   → IN_PROGRESS             (re-attempt allowed up to maxAttempts)
 *  COMPLETED → EXPIRED                (certificate validity lapsed)
 *  ENROLLED  → WAIVED                 (manager-approved exemption)
 *  Any       → CANCELLED              (admin revokes assignment)
 */
public enum EnrollmentStatus {
    ENROLLED,       // assigned but not started
    IN_PROGRESS,    // at least one content item viewed or quiz started
    COMPLETED,      // all required items done AND assessment passed (or no assessment)
    FAILED,         // assessment submitted but score < pass mark
    EXPIRED,        // was COMPLETED; certificate validity period has lapsed
    WAIVED,         // exempted — counts as compliant for reporting
    CANCELLED       // revoked by an admin; does not count as compliant
}
