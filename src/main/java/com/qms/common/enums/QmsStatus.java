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

    /**
     * CAPA-only — sits between PENDING_VERIFICATION (HOD writes verification
     * narrative) and CLOSED (Head QA closes). The QA Reviewer reviews the
     * verification + evidence and accepts or rejects it. Per the CAPA flow
     * chart this is a discrete stage; collapsing it into PENDING_VERIFICATION
     * would lose the dual-actor audit trail (HOD-author vs QA-reviewer).
     */
    PENDING_VERIFICATION_REVIEW,

    // ── Terminal / Exception states ──────────────────────────
    REJECTED,                 // Returned for correction / rework
    CLOSED,                   // Completed and formally closed
    CANCELLED,                // Abandoned — no further action
    REOPENED,                 // Re-opened from CLOSED (transitions to DRAFT)

    // ── Cross-module handoff terminals ───────────────────────
    /**
     * General Incident hands off to the Deviation module (when the HOD ticks
     * deviation_required = TRUE and QA confirms it). The Incident itself
     * terminates here; the spawned Deviation continues independently with
     * parent_incident_id pointing back. The spawned Deviation's number is
     * stamped on the Incident's spawned_deviation_number column for the
     * cross-link UI.
     */
    DEVIATION_SPAWNED,

    // ── CAPA post-closure effectiveness-assessment lifecycle ─
    //
    // Once Head QA closes a CAPA (status = CLOSED), the responsible
    // department runs through one or more scheduled effectiveness-
    // assessment cycles. Each cycle is tracked as a row in
    // qms_capa_assessments; the parent CAPA's status moves through these
    // sub-states to summarise where the assessment lifecycle is overall.

    /**
     * The CAPA is closed and at least one scheduled assessment row is
     * waiting for the responsible department to fill its observed-
     * effectiveness narrative. Once the dept submits, the row moves to
     * EFFECTIVENESS_REVIEW; the parent CAPA stays in this state until
     * every scheduled cycle has been reviewed.
     */
    EFFECTIVENESS_PENDING,

    /**
     * The responsible dept has submitted an assessment cycle and it's
     * waiting for QA Reviewer's acceptance / rejection. The parent CAPA
     * cycles between EFFECTIVENESS_PENDING and EFFECTIVENESS_REVIEW
     * across each scheduled cycle.
     */
    EFFECTIVENESS_REVIEW,

    /**
     * Every scheduled assessment cycle has been completed AND accepted.
     * The CAPA is verified effective — final terminal state.
     */
    EFFECTIVENESS_VERIFIED
}
