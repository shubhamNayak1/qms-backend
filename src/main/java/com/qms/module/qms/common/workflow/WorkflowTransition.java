package com.qms.module.qms.common.workflow;

import com.qms.common.enums.QmsRecordType;
import com.qms.common.enums.QmsStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.qms.common.enums.QmsStatus.*;

/**
 * Per-module workflow transition rules for all five QMS sub-modules.
 *
 * Each module has its own directed graph of allowed status transitions.
 * The generic transition() and shorthand methods in QmsWorkflowEngine
 * delegate here for validation.
 *
 * Flow summaries:
 * ─────────────────────────────────────────────────────────────────────
 * CAPA:
 *   DRAFT→PENDING_HOD→PENDING_QA_REVIEW↔PENDING_DEPT_COMMENT
 *   →PENDING_HEAD_QA→CLOSED
 *
 * DEVIATION:
 *   DRAFT→PENDING_HOD→PENDING_QA_REVIEW→PENDING_RA_REVIEW
 *   →[PENDING_SITE_HEAD]→PENDING_INVESTIGATION→PENDING_VERIFICATION→CLOSED
 *
 * INCIDENT:
 *   DRAFT→PENDING_HOD→PENDING_INVESTIGATION→[PENDING_ATTACHMENTS]
 *   →PENDING_VERIFICATION→PENDING_HEAD_QA→CLOSED
 *
 * CHANGE_CONTROL:
 *   DRAFT→PENDING_HOD→PENDING_QA_REVIEW→PENDING_DEPT_COMMENT→PENDING_RA_REVIEW
 *   →[PENDING_SITE_HEAD]→[PENDING_CUSTOMER_COMMENT]→PENDING_HEAD_QA→PENDING_VERIFICATION→CLOSED
 *
 * MARKET_COMPLAINT:
 *   DRAFT→PENDING_HOD→PENDING_INVESTIGATION→PENDING_ATTACHMENTS
 *   →PENDING_VERIFICATION→CLOSED
 *
 * All modules: any non-terminal state → REJECTED → DRAFT (for rework)
 *              any non-terminal state → CANCELLED (terminal)
 *              CLOSED → REOPENED → DRAFT
 * ─────────────────────────────────────────────────────────────────────
 */
public final class WorkflowTransition {

    private WorkflowTransition() {}

    // ── Per-module transition maps ────────────────────────────

    private static final Map<QmsStatus, Set<QmsStatus>> CAPA_T       = new EnumMap<>(QmsStatus.class);
    private static final Map<QmsStatus, Set<QmsStatus>> DEVIATION_T  = new EnumMap<>(QmsStatus.class);
    private static final Map<QmsStatus, Set<QmsStatus>> INCIDENT_T   = new EnumMap<>(QmsStatus.class);
    private static final Map<QmsStatus, Set<QmsStatus>> CC_T         = new EnumMap<>(QmsStatus.class);
    private static final Map<QmsStatus, Set<QmsStatus>> MC_T         = new EnumMap<>(QmsStatus.class);

    static {
        // ── CAPA ─────────────────────────────────────────────
        CAPA_T.put(DRAFT,                Set.of(PENDING_HOD, CANCELLED));
        CAPA_T.put(PENDING_HOD,          Set.of(PENDING_QA_REVIEW, REJECTED, CANCELLED));
        CAPA_T.put(PENDING_QA_REVIEW,    Set.of(PENDING_DEPT_COMMENT, PENDING_HEAD_QA, REJECTED, CANCELLED));
        CAPA_T.put(PENDING_DEPT_COMMENT, Set.of(PENDING_QA_REVIEW));
        CAPA_T.put(PENDING_HEAD_QA,      Set.of(CLOSED, REJECTED));
        CAPA_T.put(REJECTED,             Set.of(DRAFT, CANCELLED));
        CAPA_T.put(CLOSED,               Set.of(REOPENED));
        CAPA_T.put(REOPENED,             Set.of(DRAFT, CANCELLED));
        CAPA_T.put(CANCELLED,            Set.of());

        // ── DEVIATION ─────────────────────────────────────────
        DEVIATION_T.put(DRAFT,                Set.of(PENDING_HOD, CANCELLED));
        DEVIATION_T.put(PENDING_HOD,          Set.of(PENDING_QA_REVIEW, REJECTED, CANCELLED));
        DEVIATION_T.put(PENDING_QA_REVIEW,    Set.of(PENDING_RA_REVIEW, REJECTED, CANCELLED));
        DEVIATION_T.put(PENDING_RA_REVIEW,    Set.of(PENDING_SITE_HEAD, PENDING_INVESTIGATION, REJECTED, CANCELLED));
        DEVIATION_T.put(PENDING_SITE_HEAD,    Set.of(PENDING_INVESTIGATION, REJECTED, CANCELLED));
        DEVIATION_T.put(PENDING_INVESTIGATION,Set.of(PENDING_VERIFICATION, CANCELLED));
        DEVIATION_T.put(PENDING_VERIFICATION, Set.of(CLOSED, REJECTED));
        DEVIATION_T.put(REJECTED,             Set.of(DRAFT, CANCELLED));
        DEVIATION_T.put(CLOSED,               Set.of(REOPENED));
        DEVIATION_T.put(REOPENED,             Set.of(DRAFT, CANCELLED));
        DEVIATION_T.put(CANCELLED,            Set.of());

        // ── INCIDENT ──────────────────────────────────────────
        INCIDENT_T.put(DRAFT,                Set.of(PENDING_HOD, CANCELLED));
        INCIDENT_T.put(PENDING_HOD,          Set.of(PENDING_INVESTIGATION, REJECTED, CANCELLED));
        INCIDENT_T.put(PENDING_INVESTIGATION,Set.of(PENDING_ATTACHMENTS, PENDING_VERIFICATION, CANCELLED));
        INCIDENT_T.put(PENDING_ATTACHMENTS,  Set.of(PENDING_VERIFICATION));
        INCIDENT_T.put(PENDING_VERIFICATION, Set.of(PENDING_HEAD_QA));
        INCIDENT_T.put(PENDING_HEAD_QA,      Set.of(CLOSED, REJECTED));
        INCIDENT_T.put(REJECTED,             Set.of(DRAFT, CANCELLED));
        INCIDENT_T.put(CLOSED,               Set.of(REOPENED));
        INCIDENT_T.put(REOPENED,             Set.of(DRAFT, CANCELLED));
        INCIDENT_T.put(CANCELLED,            Set.of());

        // ── CHANGE_CONTROL ────────────────────────────────────
        CC_T.put(DRAFT,                    Set.of(PENDING_HOD, CANCELLED));
        CC_T.put(PENDING_HOD,              Set.of(PENDING_QA_REVIEW, REJECTED, CANCELLED));
        CC_T.put(PENDING_QA_REVIEW,        Set.of(PENDING_DEPT_COMMENT, REJECTED, CANCELLED));
        CC_T.put(PENDING_DEPT_COMMENT,     Set.of(PENDING_RA_REVIEW));
        CC_T.put(PENDING_RA_REVIEW,        Set.of(PENDING_SITE_HEAD, PENDING_HEAD_QA, REJECTED, CANCELLED));
        CC_T.put(PENDING_SITE_HEAD,        Set.of(PENDING_CUSTOMER_COMMENT, PENDING_HEAD_QA, REJECTED, CANCELLED));
        CC_T.put(PENDING_CUSTOMER_COMMENT, Set.of(PENDING_HEAD_QA));
        CC_T.put(PENDING_HEAD_QA,          Set.of(PENDING_VERIFICATION, REJECTED));
        CC_T.put(PENDING_VERIFICATION,     Set.of(CLOSED));
        CC_T.put(REJECTED,                 Set.of(DRAFT, CANCELLED));
        CC_T.put(CLOSED,                   Set.of(REOPENED));
        CC_T.put(REOPENED,                 Set.of(DRAFT, CANCELLED));
        CC_T.put(CANCELLED,                Set.of());

        // ── MARKET_COMPLAINT ──────────────────────────────────
        MC_T.put(DRAFT,                Set.of(PENDING_HOD, CANCELLED));
        MC_T.put(PENDING_HOD,          Set.of(PENDING_INVESTIGATION, REJECTED, CANCELLED));
        MC_T.put(PENDING_INVESTIGATION,Set.of(PENDING_ATTACHMENTS, CANCELLED));
        MC_T.put(PENDING_ATTACHMENTS,  Set.of(PENDING_VERIFICATION));
        MC_T.put(PENDING_VERIFICATION, Set.of(CLOSED, REJECTED));
        MC_T.put(REJECTED,             Set.of(DRAFT, CANCELLED));
        MC_T.put(CLOSED,               Set.of(REOPENED));
        MC_T.put(REOPENED,             Set.of(DRAFT, CANCELLED));
        MC_T.put(CANCELLED,            Set.of());
    }

    /**
     * Primary "approve / forward" target for each module and status.
     * Used by {@link QmsWorkflowEngine#approve(QmsRecord, String)} to pick
     * the canonical next step when the user clicks "Approve".
     * Optional-paths (PENDING_SITE_HEAD, PENDING_CUSTOMER_COMMENT, PENDING_ATTACHMENTS)
     * can still be reached via the generic transition() endpoint.
     */
    private static final Map<QmsRecordType, Map<QmsStatus, QmsStatus>> PRIMARY_FORWARD =
            new EnumMap<>(QmsRecordType.class);

    static {
        Map<QmsStatus, QmsStatus> capaFwd = new EnumMap<>(QmsStatus.class);
        capaFwd.put(DRAFT,                PENDING_HOD);
        capaFwd.put(PENDING_HOD,          PENDING_QA_REVIEW);
        capaFwd.put(PENDING_QA_REVIEW,    PENDING_HEAD_QA);
        capaFwd.put(PENDING_DEPT_COMMENT, PENDING_QA_REVIEW);
        capaFwd.put(PENDING_HEAD_QA,      CLOSED);
        PRIMARY_FORWARD.put(QmsRecordType.CAPA, capaFwd);

        Map<QmsStatus, QmsStatus> devFwd = new EnumMap<>(QmsStatus.class);
        devFwd.put(DRAFT,                PENDING_HOD);
        devFwd.put(PENDING_HOD,          PENDING_QA_REVIEW);
        devFwd.put(PENDING_QA_REVIEW,    PENDING_RA_REVIEW);
        devFwd.put(PENDING_RA_REVIEW,    PENDING_INVESTIGATION); // skip site head by default
        devFwd.put(PENDING_SITE_HEAD,    PENDING_INVESTIGATION);
        devFwd.put(PENDING_INVESTIGATION,PENDING_VERIFICATION);
        devFwd.put(PENDING_VERIFICATION, CLOSED);
        PRIMARY_FORWARD.put(QmsRecordType.DEVIATION, devFwd);

        Map<QmsStatus, QmsStatus> incFwd = new EnumMap<>(QmsStatus.class);
        incFwd.put(DRAFT,                PENDING_HOD);
        incFwd.put(PENDING_HOD,          PENDING_INVESTIGATION);
        incFwd.put(PENDING_INVESTIGATION,PENDING_VERIFICATION); // skip attachments by default
        incFwd.put(PENDING_ATTACHMENTS,  PENDING_VERIFICATION);
        incFwd.put(PENDING_VERIFICATION, PENDING_HEAD_QA);
        incFwd.put(PENDING_HEAD_QA,      CLOSED);
        PRIMARY_FORWARD.put(QmsRecordType.INCIDENT, incFwd);

        Map<QmsStatus, QmsStatus> ccFwd = new EnumMap<>(QmsStatus.class);
        ccFwd.put(DRAFT,                    PENDING_HOD);
        ccFwd.put(PENDING_HOD,              PENDING_QA_REVIEW);
        ccFwd.put(PENDING_QA_REVIEW,        PENDING_DEPT_COMMENT);
        ccFwd.put(PENDING_DEPT_COMMENT,     PENDING_RA_REVIEW);
        ccFwd.put(PENDING_RA_REVIEW,        PENDING_HEAD_QA); // skip site head by default
        ccFwd.put(PENDING_SITE_HEAD,        PENDING_HEAD_QA); // skip customer comment by default
        ccFwd.put(PENDING_CUSTOMER_COMMENT, PENDING_HEAD_QA);
        ccFwd.put(PENDING_HEAD_QA,          PENDING_VERIFICATION);
        ccFwd.put(PENDING_VERIFICATION,     CLOSED);
        PRIMARY_FORWARD.put(QmsRecordType.CHANGE_CONTROL, ccFwd);

        Map<QmsStatus, QmsStatus> mcFwd = new EnumMap<>(QmsStatus.class);
        mcFwd.put(DRAFT,                PENDING_HOD);
        mcFwd.put(PENDING_HOD,          PENDING_INVESTIGATION);
        mcFwd.put(PENDING_INVESTIGATION,PENDING_ATTACHMENTS);
        mcFwd.put(PENDING_ATTACHMENTS,  PENDING_VERIFICATION);
        mcFwd.put(PENDING_VERIFICATION, CLOSED);
        PRIMARY_FORWARD.put(QmsRecordType.MARKET_COMPLAINT, mcFwd);
    }

    // ── Public API ────────────────────────────────────────────

    private static Map<QmsStatus, Set<QmsStatus>> mapFor(QmsRecordType type) {
        return switch (type) {
            case CAPA             -> CAPA_T;
            case DEVIATION        -> DEVIATION_T;
            case INCIDENT         -> INCIDENT_T;
            case CHANGE_CONTROL   -> CC_T;
            case MARKET_COMPLAINT -> MC_T;
            default               -> Map.of();
        };
    }

    public static boolean isAllowed(QmsRecordType type, QmsStatus from, QmsStatus to) {
        return mapFor(type).getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<QmsStatus> allowedFrom(QmsRecordType type, QmsStatus current) {
        return mapFor(type).getOrDefault(current, Set.of());
    }

    /**
     * Returns the canonical "approve" target for the given module and current status.
     * Empty if the current status has no defined primary forward path (e.g., CLOSED).
     */
    public static Optional<QmsStatus> primaryApprovalTarget(QmsRecordType type, QmsStatus current) {
        Map<QmsStatus, QmsStatus> fwd = PRIMARY_FORWARD.get(type);
        return fwd == null ? Optional.empty() : Optional.ofNullable(fwd.get(current));
    }

    public static String transitionError(QmsRecordType type, QmsStatus from, QmsStatus to) {
        return String.format(
                "Cannot transition %s record from %s to %s. Allowed next statuses: %s",
                type, from, to, allowedFrom(type, from));
    }
}
