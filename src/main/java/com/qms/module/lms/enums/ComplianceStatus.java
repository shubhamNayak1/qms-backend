package com.qms.module.lms.enums;

/**
 * Status of a trainee's ComplianceSubmission.
 *
 *  PENDING   — Submitted by trainee; awaiting coordinator / HR / QA review.
 *  APPROVED  — Reviewer accepted the submission.
 *  REJECTED  — Reviewer rejected; trainee must re-submit or is marked FAILED.
 */
public enum ComplianceStatus {
    PENDING,
    APPROVED,
    REJECTED
}
