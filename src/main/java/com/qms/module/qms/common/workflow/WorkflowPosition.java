package com.qms.module.qms.common.workflow;

import com.qms.common.enums.QmsStatus;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maps a *target* status to the structural position the actor must hold
 * in order to drive a record into that status.
 *
 * Statuses that are terminal or non-restricted (DRAFT, CANCELLED, REJECTED,
 * REOPENED) are intentionally absent — the workflow graph alone gates them.
 *
 * Resolution order at runtime (see {@link com.qms.module.qms.common.workflow.QmsWorkflowEngine}):
 *   1. SUPER_ADMIN   — bypasses every positional check.
 *   2. The actor's structural role is checked against the target's
 *      requirement; if it doesn't match, FORBIDDEN.
 */
public enum WorkflowPosition {
    /** Any authenticated user — used for DRAFT-to-PENDING_HOD submissions. */
    ANY_INITIATOR,
    /** HOD of the *originating* department of the record. */
    HOD_OF_RECORD_DEPT,
    /** HOD of the department currently flagged as commenting (PENDING_DEPT_COMMENT branch). */
    HOD_OF_COMMENTING_DEPT,
    /** Member of any QA-typed department who is flagged is_qa_reviewer. */
    QA_REVIEWER,
    /** HOD of any QA-typed department. */
    QA_HEAD,
    /** Member of any RA-typed department. */
    RA,
    /** Site Head as referenced by sites.head_user_id. */
    SITE_HEAD;

    private static final Map<QmsStatus, WorkflowPosition> REQUIRED = new EnumMap<>(QmsStatus.class);

    static {
        REQUIRED.put(QmsStatus.PENDING_HOD,             ANY_INITIATOR);
        REQUIRED.put(QmsStatus.PENDING_QA_REVIEW,       HOD_OF_RECORD_DEPT);
        REQUIRED.put(QmsStatus.PENDING_DEPT_COMMENT,    QA_REVIEWER);
        REQUIRED.put(QmsStatus.PENDING_RA_REVIEW,       QA_REVIEWER);
        REQUIRED.put(QmsStatus.PENDING_SITE_HEAD,       RA);
        REQUIRED.put(QmsStatus.PENDING_HEAD_QA,         QA_REVIEWER);
        REQUIRED.put(QmsStatus.PENDING_INVESTIGATION,   QA_REVIEWER);
        REQUIRED.put(QmsStatus.PENDING_VERIFICATION,    QA_REVIEWER);
        REQUIRED.put(QmsStatus.CLOSED,                  QA_HEAD);
        // DRAFT, CANCELLED, REJECTED, REOPENED, PENDING_ATTACHMENTS,
        // PENDING_CUSTOMER_COMMENT — left unrestricted so the existing
        // graph rules govern them. SUPER_ADMIN can always force-move.
    }

    /**
     * @return the required position for moving a record INTO {@code target},
     *         or null if no positional check applies (any allowed transition wins).
     */
    public static WorkflowPosition requiredFor(QmsStatus target) {
        return target == null ? null : REQUIRED.get(target);
    }

    /**
     * Source-aware override. Most transitions are owned solely by their
     * target's actor (e.g. only the QA Reviewer can drive anything to
     * PENDING_RA_REVIEW). A few legitimate loop-backs need to be owned
     * by the actor at the SOURCE state instead:
     *
     *  • PENDING_DEPT_COMMENT → PENDING_QA_REVIEW   (CHANGE_CONTROL / CAPA)
     *  • PENDING_DEPT_COMMENT → PENDING_INVESTIGATION (MARKET_COMPLAINT)
     *      The HOD of the commenting dept (who's filling their feedback
     *      in the accordion) needs to be able to bounce the record back
     *      if they spot a problem during commenting, AND the QA Reviewer
     *      needs to be able to advance after every dept has filled. The
     *      engine's HOD_OF_COMMENTING_DEPT check accepts both actors
     *      (commenting-dept HOD or QA Reviewer/Head) so that single
     *      target works for both scenarios.
     */
    public static WorkflowPosition requiredFor(QmsStatus from, QmsStatus to) {
        if (from == QmsStatus.PENDING_DEPT_COMMENT && to == QmsStatus.PENDING_QA_REVIEW) {
            return HOD_OF_COMMENTING_DEPT;
        }
        if (from == QmsStatus.PENDING_DEPT_COMMENT && to == QmsStatus.PENDING_INVESTIGATION) {
            return HOD_OF_COMMENTING_DEPT;
        }
        return requiredFor(to);
    }
}
