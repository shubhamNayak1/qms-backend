package com.qms.module.lms.enums;

/**
 * Lifecycle of one trainee's enrollment in a TrainingProgram.
 *
 *  ALLOCATED            — assigned by manager, not yet started
 *  IN_PROGRESS          — trainee is attending / reading / watching
 *  PENDING_REVIEW       — trainee submitted compliance; awaiting coordinator review
 *  PENDING_HR_REVIEW    — induction only: awaiting HR review
 *  PENDING_QA_APPROVAL  — induction only: awaiting QA Head approval
 *  COMPLETED            — all done and passed (exam + compliance)
 *  FAILED               — compliance rejected OR exam failed (attempts exhausted)
 *  RETRAINING           — failed; a new retraining enrollment has been created
 *  EXPIRED              — was COMPLETED; certificate validity has lapsed
 *  WAIVED               — manager-approved exemption; counts as compliant
 *  CANCELLED            — revoked by admin; does NOT count as compliant
 *
 *  Note: ENROLLED kept for backward compatibility with existing data.
 */
public enum EnrollmentStatus {
    ENROLLED,             // legacy alias for ALLOCATED
    ALLOCATED,            // assigned, not yet started
    IN_PROGRESS,
    PENDING_REVIEW,
    PENDING_HR_REVIEW,
    PENDING_QA_APPROVAL,
    COMPLETED,
    FAILED,
    RETRAINING,
    EXPIRED,
    WAIVED,
    CANCELLED
}
