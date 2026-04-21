package com.qms.module.lms.enums;

public enum AssessmentStatus {
    NOT_STARTED,
    IN_PROGRESS,
    SUBMITTED,      // answers submitted, awaiting auto-grading
    PASSED,
    FAILED,
    PENDING_REVIEW  // SHORT_ANSWER questions awaiting manual grading
}
