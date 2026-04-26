package com.qms.common.enums;

/**
 * Granular status lifecycle for all QMS record types.
 * Each module uses a subset of these statuses — see WorkflowTransition for per-module graphs.
 *
 * Common terminal states: CLOSED, CANCELLED
 * All modules start at DRAFT and end at CLOSED.
 */
public enum QmsStatus {

    // ── Initial state ────────────────────────────────────────
    DRAFT,                    // Record created, not yet submitted for review

    // ── Review / Evaluation stages ───────────────────────────
    PENDING_HOD,              // Awaiting Head of Department review/forwarding
    PENDING_QA_REVIEW,        // Under QA evaluation (used in CAPA, Deviation, Change Control)
    PENDING_DEPT_COMMENT,     // Awaiting department feedback / comments (CAPA, Change Control)
    PENDING_RA_REVIEW,        // Regulatory Affairs evaluation (Deviation, Change Control)
    PENDING_SITE_HEAD,        // Awaiting Site Head approval (Deviation, Change Control — optional)
    PENDING_CUSTOMER_COMMENT, // Awaiting customer comment (Change Control — optional)
    PENDING_HEAD_QA,          // Awaiting Head of QA final decision (CAPA, Incident, Change Control)

    // ── Investigation / Evidence stages ──────────────────────
    PENDING_INVESTIGATION,    // Active QA investigation in progress (Incident, Market Complaint, Deviation)
    PENDING_ATTACHMENTS,      // Awaiting lab results / supporting data (Incident, Market Complaint)

    // ── Closure stages ───────────────────────────────────────
    PENDING_VERIFICATION,     // Final verification of corrective actions (all modules)

    // ── Terminal / Exception states ──────────────────────────
    REJECTED,                 // Returned for correction / rework
    CLOSED,                   // Completed and formally closed
    CANCELLED,                // Abandoned — no further action
    REOPENED                  // Re-opened from CLOSED (transitions to DRAFT)
}
