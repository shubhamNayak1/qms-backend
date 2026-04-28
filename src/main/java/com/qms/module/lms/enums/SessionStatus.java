package com.qms.module.lms.enums;

/**
 * Lifecycle of a single TrainingSession (a scheduled run of a program).
 *
 *  SCHEDULED   — Date/venue confirmed, not yet started.
 *  IN_PROGRESS — Session is currently underway (within ±2 day window).
 *  COMPLETED   — Session finished; attendance marked.
 *  CANCELLED   — Session cancelled by coordinator/manager.
 */
public enum SessionStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
