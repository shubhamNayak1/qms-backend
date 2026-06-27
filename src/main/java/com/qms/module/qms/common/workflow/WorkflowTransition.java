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
 * CAPA (Kedar-sir spec):
 *   DRAFT→PENDING_HOD (Proposed CAPA: HOD's Initial Remedial + Preventive)
 *   →PENDING_QA_REVIEW ↔ PENDING_DEPT_COMMENT (cross-functional fan-out)
 *   →[PENDING_SITE_HEAD]→PENDING_HEAD_QA
 *   →PENDING_ATTACHMENTS (dept attachments — all rows must be APPROVED)
 *   →PENDING_VERIFICATION (Verification/Add — dept HOD's Action Taken)
 *   →PENDING_VERIFICATION_REVIEW (QA Reviewer accepts the verification)
 *   →CLOSED (Head QA — sets effectiveness assessment frequency + count)
 *   Post-closure effectiveness lifecycle:
 *   CLOSED→EFFECTIVENESS_PENDING ↔ EFFECTIVENESS_REVIEW
 *         →EFFECTIVENESS_VERIFIED (terminal — every cycle accepted)
 *
 * DEVIATION (Kedar-sir spec):
 *   DRAFT→PENDING_HOD (HOD assessment + optional CAPA cross-link)
 *   →PENDING_QA_REVIEW ↔ PENDING_DEPT_COMMENT (cross-functional fan-out)
 *   →PENDING_RA_REVIEW + [PENDING_CUSTOMER_COMMENT in parallel]
 *   →[PENDING_SITE_HEAD]→PENDING_HEAD_QA
 *   →PENDING_ATTACHMENTS (dept attachments, all rows must be APPROVED)
 *   →PENDING_VERIFICATION (Investigation Summary)→CLOSED
 *
 * INCIDENT (Kedar-sir spec — four end-to-end paths):
 *   DRAFT→PENDING_HOD (HOD assessment + branching flags)
 *   →PENDING_QA_REVIEW
 *      ↔ PENDING_DEPT_COMMENT  (General + No-Dev path only)
 *   →[PENDING_SITE_HEAD]→PENDING_HEAD_QA
 *   →PENDING_ATTACHMENTS→PENDING_VERIFICATION→CLOSED
 *
 *   Special handoff: General + Deviation Required path goes
 *   PENDING_QA_REVIEW → DEVIATION_SPAWNED (terminal). The spawned
 *   Deviation continues independently with parent_incident_id set.
 *
 * CHANGE_CONTROL:
 *   DRAFT→PENDING_HOD→PENDING_QA_REVIEW→PENDING_DEPT_COMMENT→PENDING_RA_REVIEW
 *   →[PENDING_SITE_HEAD]→[PENDING_CUSTOMER_COMMENT]→PENDING_HEAD_QA→PENDING_VERIFICATION→CLOSED
 *
 * MARKET_COMPLAINT:
 *   DRAFT→PENDING_HOD (HOD review only — no dept routing here)
 *   →PENDING_INVESTIGATION ↔ PENDING_DEPT_COMMENT
 *   →PENDING_HEAD_QA→CLOSED
 *
 *   QA Reviewer drives the investigation hub: invites depts (PENDING_DEPT_COMMENT)
 *   and once every dept comment is filled the record loops back to
 *   PENDING_INVESTIGATION before the QA Reviewer forwards it to Head QA.
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
        // Per the May 2026 Kedar-sir flow chart. Two-pass QA Review with
        // dept-comment fan-out, optional Site Head, dept-attachment gate,
        // and a verification-review split (HOD writes verification, QA
        // reviews it). Closure seeds the post-closure effectiveness
        // lifecycle on the qms_capa_assessments sidecar.
        // Round-L (2026-06-26): peer-review gate. Initiator submits the
        // draft to PENDING_REVIEW; another user in the same department
        // (not the creator) verifies and forwards to HOD. Reviewer can
        // also send back (→ DRAFT) for edits.
        CAPA_T.put(DRAFT,                Set.of(PENDING_REVIEW, CANCELLED));
        CAPA_T.put(PENDING_REVIEW,       Set.of(PENDING_HOD, DRAFT, CANCELLED));
        // PENDING_HOD → DRAFT is the "Resend to Initiator" transition (May 2026
        // tester feedback). Distinct from REJECTED — the record stays alive;
        // the Initiator edits + re-submits. Engine increments resend_count
        // and re-assigns the record to the original raiser.
        CAPA_T.put(PENDING_HOD,          Set.of(PENDING_QA_REVIEW, DRAFT, REJECTED, CANCELLED));
        // QA Review's two passes share PENDING_QA_REVIEW. Primary forward
        // is to Head QA (skipping Site Head); secondary actions on the
        // stage panel pick "Invite Departments" or "Forward to Site Head".
        CAPA_T.put(PENDING_QA_REVIEW,    Set.of(PENDING_DEPT_COMMENT, PENDING_SITE_HEAD,
                                                 PENDING_HEAD_QA, PENDING_HOD,
                                                 DRAFT, REJECTED, CANCELLED));
        CAPA_T.put(PENDING_DEPT_COMMENT, Set.of(PENDING_QA_REVIEW, DRAFT, REJECTED, CANCELLED));
        CAPA_T.put(PENDING_SITE_HEAD,    Set.of(PENDING_HEAD_QA, PENDING_QA_REVIEW, DRAFT, REJECTED, CANCELLED));
        CAPA_T.put(PENDING_HEAD_QA,      Set.of(PENDING_ATTACHMENTS, PENDING_QA_REVIEW, DRAFT, REJECTED));
        CAPA_T.put(PENDING_ATTACHMENTS,  Set.of(PENDING_VERIFICATION, DRAFT, REJECTED, CANCELLED));
        CAPA_T.put(PENDING_VERIFICATION, Set.of(PENDING_VERIFICATION_REVIEW, DRAFT, REJECTED, CANCELLED));
        // QA Review either accepts (→ CLOSED via Head QA) or sends back
        // to dept HOD for re-verification.
        CAPA_T.put(PENDING_VERIFICATION_REVIEW,
                                          Set.of(CLOSED, PENDING_VERIFICATION, REJECTED));
        // CLOSED is gateway to either REOPENED (rare reopen for legacy
        // reasons) or the post-closure effectiveness lifecycle. We also
        // allow CLOSED → EFFECTIVENESS_PENDING when the assessment cycles
        // are seeded by Head QA at closure time.
        CAPA_T.put(CLOSED,               Set.of(REOPENED, EFFECTIVENESS_PENDING));
        CAPA_T.put(EFFECTIVENESS_PENDING, Set.of(EFFECTIVENESS_REVIEW, EFFECTIVENESS_VERIFIED));
        CAPA_T.put(EFFECTIVENESS_REVIEW,  Set.of(EFFECTIVENESS_PENDING, EFFECTIVENESS_VERIFIED, REJECTED));
        CAPA_T.put(EFFECTIVENESS_VERIFIED, Set.of());  // terminal
        CAPA_T.put(REJECTED,             Set.of(DRAFT, CANCELLED));
        CAPA_T.put(REOPENED,             Set.of(DRAFT, CANCELLED));
        CAPA_T.put(CANCELLED,            Set.of());

        // ── DEVIATION ─────────────────────────────────────────
        // Per the May 2026 Kedar-sir flow chart. Two-pass QA Review with
        // dept-comment fan-out in between, parallel RA + Customer branch,
        // optional Site Head, then Head QA → dept attachments → Investigation
        // Summary → Closed.
        // Round-L peer-review gate (see CAPA notes).
        DEVIATION_T.put(DRAFT,                Set.of(PENDING_REVIEW, CANCELLED));
        DEVIATION_T.put(PENDING_REVIEW,       Set.of(PENDING_HOD, DRAFT, CANCELLED));
        DEVIATION_T.put(PENDING_HOD,          Set.of(PENDING_QA_REVIEW, DRAFT, REJECTED, CANCELLED));
        // QA's two passes share PENDING_QA_REVIEW. From here QA can:
        //   • invite depts (PENDING_DEPT_COMMENT)
        //   • forward to RA (PENDING_RA_REVIEW) — canonical advance
        //   • route customer in parallel (PENDING_CUSTOMER_COMMENT)
        DEVIATION_T.put(PENDING_QA_REVIEW,    Set.of(PENDING_DEPT_COMMENT, PENDING_RA_REVIEW,
                                                     PENDING_CUSTOMER_COMMENT, DRAFT, REJECTED, CANCELLED));
        // Dept comments loop back to QA (and not on to RA directly), so QA
        // can re-evaluate before deciding the routing flags.
        DEVIATION_T.put(PENDING_DEPT_COMMENT, Set.of(PENDING_QA_REVIEW, DRAFT, REJECTED, CANCELLED));
        // RA can converge to Site Head OR Head QA depending on
        // site_head_required, OR to Customer Comment if QA flagged it.
        DEVIATION_T.put(PENDING_RA_REVIEW,    Set.of(PENDING_CUSTOMER_COMMENT, PENDING_SITE_HEAD,
                                                     PENDING_HEAD_QA, DRAFT, REJECTED, CANCELLED));
        // Customer Comment can converge to Site Head OR Head QA OR back to RA.
        DEVIATION_T.put(PENDING_CUSTOMER_COMMENT,
                                              Set.of(PENDING_SITE_HEAD, PENDING_HEAD_QA, PENDING_RA_REVIEW, DRAFT, REJECTED, CANCELLED));
        DEVIATION_T.put(PENDING_SITE_HEAD,    Set.of(PENDING_HEAD_QA, DRAFT, REJECTED, CANCELLED));
        DEVIATION_T.put(PENDING_HEAD_QA,      Set.of(PENDING_ATTACHMENTS, PENDING_QA_REVIEW, DRAFT, REJECTED));
        // PENDING_ATTACHMENTS — gated by dept-attachment-approval guard before
        // it can move to PENDING_VERIFICATION.
        DEVIATION_T.put(PENDING_ATTACHMENTS,  Set.of(PENDING_VERIFICATION, DRAFT, REJECTED, CANCELLED));
        DEVIATION_T.put(PENDING_VERIFICATION, Set.of(CLOSED, PENDING_ATTACHMENTS, DRAFT, REJECTED));
        DEVIATION_T.put(REJECTED,             Set.of(DRAFT, CANCELLED));
        DEVIATION_T.put(CLOSED,               Set.of(REOPENED));
        DEVIATION_T.put(REOPENED,             Set.of(DRAFT, CANCELLED));
        DEVIATION_T.put(CANCELLED,            Set.of());

        // ── INCIDENT ──────────────────────────────────────────
        // Per the May 2026 Kedar-sir flow chart. Four end-to-end paths
        // (Lab + Retest, Lab + No-Retest, General + No-Dev, General + Dev)
        // share a single graph; the path is selected by HOD's branching
        // flags (incident_sub_type, retesting_required, deviation_required)
        // and the QA Reviewer's site_head_required flag.
        // Round-L peer-review gate (see CAPA notes).
        INCIDENT_T.put(DRAFT,                Set.of(PENDING_REVIEW, CANCELLED));
        INCIDENT_T.put(PENDING_REVIEW,       Set.of(PENDING_HOD, DRAFT, CANCELLED));
        INCIDENT_T.put(PENDING_HOD,          Set.of(PENDING_QA_REVIEW, DRAFT, REJECTED, CANCELLED));
        // From QA Review:
        //   • General + No-Dev → invite depts (PENDING_DEPT_COMMENT)
        //   • General + Dev    → spawn Deviation (DEVIATION_SPAWNED, terminal)
        //   • Lab branches (both retest cases) skip dept comments —
        //     QA forwards to Site Head if required, else Head QA
        INCIDENT_T.put(PENDING_QA_REVIEW,    Set.of(PENDING_DEPT_COMMENT, PENDING_SITE_HEAD,
                                                    PENDING_HEAD_QA, DEVIATION_SPAWNED,
                                                    DRAFT, REJECTED, CANCELLED));
        // Dept comments loop back to QA (similar to Deviation).
        INCIDENT_T.put(PENDING_DEPT_COMMENT, Set.of(PENDING_QA_REVIEW, DRAFT, REJECTED, CANCELLED));
        INCIDENT_T.put(PENDING_SITE_HEAD,    Set.of(PENDING_HEAD_QA, DRAFT, REJECTED, CANCELLED));
        INCIDENT_T.put(PENDING_HEAD_QA,      Set.of(PENDING_ATTACHMENTS, PENDING_QA_REVIEW, DRAFT, REJECTED));
        INCIDENT_T.put(PENDING_ATTACHMENTS,  Set.of(PENDING_VERIFICATION, DRAFT, REJECTED, CANCELLED));
        INCIDENT_T.put(PENDING_VERIFICATION, Set.of(CLOSED, PENDING_ATTACHMENTS, DRAFT, REJECTED));
        INCIDENT_T.put(REJECTED,             Set.of(DRAFT, CANCELLED));
        INCIDENT_T.put(CLOSED,               Set.of(REOPENED));
        INCIDENT_T.put(REOPENED,             Set.of(DRAFT, CANCELLED));
        INCIDENT_T.put(CANCELLED,            Set.of());
        // DEVIATION_SPAWNED is terminal — the spawned Deviation continues
        // the workflow elsewhere. We deliberately don't allow any
        // transitions out of it, including REOPENED, because re-opening
        // the Incident after a Deviation has been spawned would create
        // dual-tracking confusion.
        INCIDENT_T.put(DEVIATION_SPAWNED,    Set.of());

        // ── CHANGE_CONTROL ────────────────────────────────────
        // Round-2 tester feedback: every reviewer stage allows Resend to
        // Initiator (→ DRAFT). The position rule (WorkflowPosition) gates
        // who's authorised at each source.
        // Round-L peer-review gate (see CAPA notes).
        CC_T.put(DRAFT,                    Set.of(PENDING_REVIEW, CANCELLED));
        CC_T.put(PENDING_REVIEW,           Set.of(PENDING_HOD, DRAFT, CANCELLED));
        CC_T.put(PENDING_HOD,              Set.of(PENDING_QA_REVIEW, DRAFT, REJECTED, CANCELLED));
        // Round-L (2026-06-27): QA Reviewer can now skip the dept-comment
        // loop entirely when no departments are invited and forward
        // straight to RA Evaluation. The frontend picks the target based
        // on dept count: 0 depts → PENDING_RA_REVIEW; ≥1 → PENDING_DEPT_COMMENT.
        CC_T.put(PENDING_QA_REVIEW,        Set.of(PENDING_DEPT_COMMENT, PENDING_RA_REVIEW,
                                                   DRAFT, REJECTED, CANCELLED));
        // PENDING_DEPT_COMMENT can also loop back to QA (so the dept HOD can
        // bounce the record back if QA needs to re-evaluate before
        // forwarding to RA), and supports REJECTED / CANCELLED escape hatches.
        CC_T.put(PENDING_DEPT_COMMENT,     Set.of(PENDING_RA_REVIEW, PENDING_QA_REVIEW, DRAFT, REJECTED, CANCELLED));
        CC_T.put(PENDING_RA_REVIEW,        Set.of(PENDING_SITE_HEAD, PENDING_HEAD_QA, DRAFT, REJECTED, CANCELLED));
        CC_T.put(PENDING_SITE_HEAD,        Set.of(PENDING_CUSTOMER_COMMENT, PENDING_HEAD_QA, DRAFT, REJECTED, CANCELLED));
        CC_T.put(PENDING_CUSTOMER_COMMENT, Set.of(PENDING_HEAD_QA, DRAFT));
        // Round-3 R28: Head QA approve now routes to PENDING_ATTACHMENTS
        // (when at least one dept comment flagged action_required = true).
        // The engine auto-creates attachment-request rows for those depts at
        // that transition. PENDING_ATTACHMENTS gates onward to VERIFICATION
        // by the existing requireDeptAttachmentsApproved guard.
        CC_T.put(PENDING_HEAD_QA,          Set.of(PENDING_ATTACHMENTS, PENDING_VERIFICATION, DRAFT, REJECTED));
        CC_T.put(PENDING_ATTACHMENTS,      Set.of(PENDING_VERIFICATION, DRAFT, REJECTED, CANCELLED));
        CC_T.put(PENDING_VERIFICATION,     Set.of(CLOSED, DRAFT));
        CC_T.put(REJECTED,                 Set.of(DRAFT, CANCELLED));
        CC_T.put(CLOSED,                   Set.of(REOPENED));
        CC_T.put(REOPENED,                 Set.of(DRAFT, CANCELLED));
        CC_T.put(CANCELLED,                Set.of());

        // ── MARKET_COMPLAINT ──────────────────────────────────
        // HOD reviews only — does NOT route to depts (that happens at the
        // QA Investigation hub). PENDING_INVESTIGATION ↔ PENDING_DEPT_COMMENT
        // is a true loop: QA invites dept comments, the record sits at
        // PENDING_DEPT_COMMENT until every requested dept fills their row,
        // then comes back to PENDING_INVESTIGATION. QA Reviewer can also
        // skip dept comments entirely and forward straight to Head QA.
        // Round-L peer-review gate (see CAPA notes).
        MC_T.put(DRAFT,                Set.of(PENDING_REVIEW, CANCELLED));
        MC_T.put(PENDING_REVIEW,       Set.of(PENDING_HOD, DRAFT, CANCELLED));
        MC_T.put(PENDING_HOD,          Set.of(PENDING_INVESTIGATION, DRAFT, REJECTED, CANCELLED));
        MC_T.put(PENDING_INVESTIGATION,Set.of(PENDING_DEPT_COMMENT, PENDING_HEAD_QA, DRAFT, REJECTED, CANCELLED));
        MC_T.put(PENDING_DEPT_COMMENT, Set.of(PENDING_INVESTIGATION, DRAFT, REJECTED, CANCELLED));
        MC_T.put(PENDING_HEAD_QA,      Set.of(CLOSED, PENDING_INVESTIGATION, DRAFT, REJECTED));
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
        capaFwd.put(DRAFT,                       PENDING_REVIEW);
        capaFwd.put(PENDING_REVIEW,              PENDING_HOD);
        capaFwd.put(PENDING_HOD,                 PENDING_QA_REVIEW);
        // QA's canonical forward target is Head QA (skipping site head + dept
        // comments). Stage panel exposes explicit secondaries for "Invite
        // Departments" and "Forward to Site Head".
        capaFwd.put(PENDING_QA_REVIEW,           PENDING_HEAD_QA);
        capaFwd.put(PENDING_DEPT_COMMENT,        PENDING_QA_REVIEW);
        capaFwd.put(PENDING_SITE_HEAD,           PENDING_HEAD_QA);
        capaFwd.put(PENDING_HEAD_QA,             PENDING_ATTACHMENTS);
        capaFwd.put(PENDING_ATTACHMENTS,         PENDING_VERIFICATION);
        capaFwd.put(PENDING_VERIFICATION,        PENDING_VERIFICATION_REVIEW);
        capaFwd.put(PENDING_VERIFICATION_REVIEW, CLOSED);
        capaFwd.put(EFFECTIVENESS_PENDING,       EFFECTIVENESS_REVIEW);
        capaFwd.put(EFFECTIVENESS_REVIEW,        EFFECTIVENESS_VERIFIED);
        PRIMARY_FORWARD.put(QmsRecordType.CAPA, capaFwd);

        Map<QmsStatus, QmsStatus> devFwd = new EnumMap<>(QmsStatus.class);
        devFwd.put(DRAFT,                    PENDING_REVIEW);
        devFwd.put(PENDING_REVIEW,           PENDING_HOD);
        devFwd.put(PENDING_HOD,              PENDING_QA_REVIEW);
        // QA's canonical "approve" target is PENDING_RA_REVIEW. The dept-
        // comment fan-out and the customer branch are explicit secondary
        // actions on the stage panel so the QA Reviewer makes the routing
        // decision deliberately, not by default.
        devFwd.put(PENDING_QA_REVIEW,        PENDING_RA_REVIEW);
        devFwd.put(PENDING_DEPT_COMMENT,     PENDING_QA_REVIEW);
        devFwd.put(PENDING_RA_REVIEW,        PENDING_HEAD_QA);   // skip site head by default
        devFwd.put(PENDING_CUSTOMER_COMMENT, PENDING_HEAD_QA);   // skip site head by default
        devFwd.put(PENDING_SITE_HEAD,        PENDING_HEAD_QA);
        devFwd.put(PENDING_HEAD_QA,          PENDING_ATTACHMENTS);
        devFwd.put(PENDING_ATTACHMENTS,      PENDING_VERIFICATION);
        devFwd.put(PENDING_VERIFICATION,     CLOSED);
        PRIMARY_FORWARD.put(QmsRecordType.DEVIATION, devFwd);

        Map<QmsStatus, QmsStatus> incFwd = new EnumMap<>(QmsStatus.class);
        incFwd.put(DRAFT,                PENDING_REVIEW);
        incFwd.put(PENDING_REVIEW,       PENDING_HOD);
        incFwd.put(PENDING_HOD,          PENDING_QA_REVIEW);
        // QA's canonical "approve" target depends on path. We default to
        // Head QA (skipping site head + dept comments) so Lab-branch
        // approvals work in one click; the stage panel exposes explicit
        // secondaries for "Invite Departments", "Site Head", and
        // "Spawn Deviation".
        incFwd.put(PENDING_QA_REVIEW,    PENDING_HEAD_QA);
        incFwd.put(PENDING_DEPT_COMMENT, PENDING_QA_REVIEW);
        incFwd.put(PENDING_SITE_HEAD,    PENDING_HEAD_QA);
        incFwd.put(PENDING_HEAD_QA,      PENDING_ATTACHMENTS);
        incFwd.put(PENDING_ATTACHMENTS,  PENDING_VERIFICATION);
        incFwd.put(PENDING_VERIFICATION, CLOSED);
        PRIMARY_FORWARD.put(QmsRecordType.INCIDENT, incFwd);

        Map<QmsStatus, QmsStatus> ccFwd = new EnumMap<>(QmsStatus.class);
        ccFwd.put(DRAFT,                    PENDING_REVIEW);
        ccFwd.put(PENDING_REVIEW,           PENDING_HOD);
        ccFwd.put(PENDING_HOD,              PENDING_QA_REVIEW);
        ccFwd.put(PENDING_QA_REVIEW,        PENDING_DEPT_COMMENT);
        ccFwd.put(PENDING_DEPT_COMMENT,     PENDING_RA_REVIEW);
        ccFwd.put(PENDING_RA_REVIEW,        PENDING_HEAD_QA); // skip site head by default
        ccFwd.put(PENDING_SITE_HEAD,        PENDING_HEAD_QA); // skip customer comment by default
        ccFwd.put(PENDING_CUSTOMER_COMMENT, PENDING_HEAD_QA);
        // Round-3 R28: route via PENDING_ATTACHMENTS so dept-attachment rows
        // (auto-created by the engine for action_required depts) can be
        // uploaded before Verification. When no dept flagged action_required,
        // the PENDING_ATTACHMENTS stage has zero rows and the dept-attachment-
        // approval guard passes immediately so QA can advance in one click.
        ccFwd.put(PENDING_HEAD_QA,          PENDING_ATTACHMENTS);
        ccFwd.put(PENDING_ATTACHMENTS,      PENDING_VERIFICATION);
        ccFwd.put(PENDING_VERIFICATION,     CLOSED);
        PRIMARY_FORWARD.put(QmsRecordType.CHANGE_CONTROL, ccFwd);

        Map<QmsStatus, QmsStatus> mcFwd = new EnumMap<>(QmsStatus.class);
        mcFwd.put(DRAFT,                PENDING_REVIEW);
        mcFwd.put(PENDING_REVIEW,       PENDING_HOD);
        mcFwd.put(PENDING_HOD,          PENDING_INVESTIGATION);
        // QA Reviewer's canonical "approve" target depends on whether they
        // need dept input. Both targets are graph-allowed; the UI exposes
        // explicit "Invite Departments" and "Forward to Head QA" buttons
        // so the dept fan-out isn't accidental. Default forward = Head QA
        // because the dept-comment fan-out is optional in MC.
        mcFwd.put(PENDING_INVESTIGATION,PENDING_HEAD_QA);
        mcFwd.put(PENDING_DEPT_COMMENT, PENDING_INVESTIGATION);
        mcFwd.put(PENDING_HEAD_QA,      CLOSED);
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
