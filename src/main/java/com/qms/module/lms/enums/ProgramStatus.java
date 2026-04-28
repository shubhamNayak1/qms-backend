package com.qms.module.lms.enums;

/**
 * Full lifecycle of a TrainingProgram (the course template).
 *
 *  DRAFT            → UNDER_REVIEW  (training manager raises for review)
 *  UNDER_REVIEW     → APPROVED      (QA Manager approves)
 *  UNDER_REVIEW     → REJECTED      (QA sends back; program returns to DRAFT on rework)
 *  REJECTED         → DRAFT         (creator acknowledges and reworks)
 *  APPROVED         → PLANNED       (sessions scheduled with date/venue)
 *  PLANNED          → ACTIVE        (allocation approved by manager)
 *  ACTIVE           → COMPLETED     (all sessions finished)
 *  COMPLETED/ACTIVE → ARCHIVED      (retired — no new enrollments)
 */
public enum ProgramStatus {
    DRAFT,
    UNDER_REVIEW,
    APPROVED,
    PLANNED,
    ACTIVE,
    COMPLETED,
    REJECTED,
    ARCHIVED
}
