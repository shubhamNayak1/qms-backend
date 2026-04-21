package com.qms.common.enums;

/**
 * Universal status lifecycle for all QMS record types.
 *
 * Workflow graph:
 *
 *   OPEN ──► IN_PROGRESS ──► PENDING_APPROVAL ──► APPROVED ──► CLOSED
 *     │           │                                    │
 *     │           └──────────────────────────────► REJECTED ──► IN_PROGRESS (retry)
 *     │
 *     └──────────────────────────────────────────► CANCELLED
 *
 * Terminal states: CLOSED, CANCELLED
 * Reopenable:      CLOSED → REOPENED → IN_PROGRESS
 */
public enum QmsStatus {

    OPEN,               // record created, not yet picked up
    IN_PROGRESS,        // being actively worked on
    PENDING_APPROVAL,   // submitted for review
    APPROVED,           // approved but not yet closed
    REJECTED,           // sent back for rework
    CLOSED,             // completed and closed out
    CANCELLED,          // abandoned
    REOPENED            // closed but re-opened for further action
}
