package com.qms.module.lms.enums;

/**
 * Lifecycle of a TrainingProgram (the course template, not an assignment).
 *
 *  DRAFT → ACTIVE → ARCHIVED
 */
public enum ProgramStatus {
    DRAFT,      // being built by a training manager — not assignable yet
    ACTIVE,     // published and available for enrollment
    ARCHIVED    // retired — no new enrollments; historical records preserved
}
