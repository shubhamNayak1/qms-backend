package com.qms.module.qms.common.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.enums.QmsStatus;
import com.qms.common.exception.AppException;
import com.qms.module.audit.context.AuditContext;
import com.qms.module.audit.context.AuditContextHolder;
import com.qms.module.org.service.OrgSecurityService;
import com.qms.module.qms.common.entity.QmsRecord;
import com.qms.module.qms.common.repository.QmsDepartmentAttachmentRepository;
import com.qms.module.qms.common.repository.QmsDepartmentCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic workflow engine — handles per-module status transitions.
 *
 * Each QmsRecord carries its recordType, which is used to look up the
 * correct transition graph in WorkflowTransition.
 *
 * Shorthand methods:
 *  submit()  — DRAFT → PENDING_HOD (start review)
 *  approve() — advance to canonical next step per module (skips optional branches)
 *  reject()  — current → REJECTED
 *  close()   — current → CLOSED (only if CLOSED is an allowed next status)
 *  cancel()  — current → CANCELLED
 *  reopen()  — CLOSED → DRAFT
 *
 * For optional branches (PENDING_SITE_HEAD, PENDING_CUSTOMER_COMMENT,
 * PENDING_ATTACHMENTS) use transition() with an explicit targetStatus.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QmsWorkflowEngine {

    private final ObjectMapper                      mapper;
    private final OrgSecurityService                orgSecurity;
    private final QmsDepartmentCommentRepository    deptCommentRepository;
    private final QmsDepartmentAttachmentRepository deptAttachmentRepository;

    private static final TypeReference<List<StatusHistoryEntry>> HISTORY_TYPE =
            new TypeReference<>() {};

    // ── Public API ────────────────────────────────────────────

    /**
     * Generic transition — validates per-module rules and applies the status change.
     * A non-blank comment is mandatory on every workflow action — this is enforced
     * in {@link #requireComment(String)} so callers receive a single, consistent
     * error and an audit trail entry is always meaningful.
     */
    public void transition(QmsRecord record, QmsStatus newStatus, String comment) {
        QmsStatus current = record.getStatus();
        // Round-N (2026-07-04) tester CC-Point-2 · Issue 4: the QA
        // Evaluation Phase-1 forward (PENDING_QA_REVIEW → PENDING_DEPT_COMMENT)
        // is a "route to depts for comment" step where the Remark /
        // Justification is intentionally optional. The frontend already
        // treats it as optional but the backend requireComment(...) check
        // was still 400ing every save with an empty comment. Skip the
        // hard requirement for this specific transition and fall back to
        // an audit-trail placeholder so the log row still says something
        // useful. All other transitions still require a comment.
        boolean remarkOptional = (current == QmsStatus.PENDING_QA_REVIEW
                                   && newStatus == QmsStatus.PENDING_DEPT_COMMENT);
        if (remarkOptional && (comment == null || comment.isBlank())) {
            comment = "(no additional remark — routed to departments for comment)";
        } else {
            requireComment(comment);
        }
        if (current == newStatus) {
            throw AppException.badRequest("Record is already in status " + current);
        }
        if (!WorkflowTransition.isAllowed(record.getRecordType(), current, newStatus)) {
            throw AppException.badRequest(
                    WorkflowTransition.transitionError(record.getRecordType(), current, newStatus));
        }
        // ── Positional authorisation ─────────────────────────────
        // Beyond the graph rules, the actor must hold the structural role
        // required for the target status (HOD of dept, QA Reviewer, etc.).
        // SUPER_ADMIN bypasses this gate. Source-aware override applies for
        // legitimate loop-backs (e.g. PENDING_DEPT_COMMENT → PENDING_QA_REVIEW
        // is owned by the HOD of the commenting dept, not by the HOD of
        // record's originating dept).
        requirePosition(record, current, newStatus);

        // ── Cross-cutting state guards ───────────────────────────
        // Block forward-progression to RA Review (CC) / QA Investigation (MC)
        // until every QmsDepartmentComment requested for this record is
        // COMPLETED. Otherwise QA Reviewers can bypass the dept fan-out by
        // clicking Approve too early.
        requireDeptCommentsComplete(record, current, newStatus);

        // Market Complaint 45-day SLA: closure beyond 45 days from creation
        // is only allowed when an approved target-date extension is on
        // record. Forces the regulator-required extension flow.
        requireMcExtensionForLateClose(record, current, newStatus);

        // Deviation: every dept-attachment row must be APPROVED before the
        // record can move from PENDING_ATTACHMENTS to PENDING_VERIFICATION
        // (see flow chart's "All Department Attachment Should approved").
        requireDeptAttachmentsApproved(record, current, newStatus);

        // Deviation 30-day SLA — same pattern as the MC 45-day rule. Forces
        // an approved target-date extension when closure is late.
        requireExtensionForLateClose30(record, current, newStatus);

        applyTransition(record, current, newStatus, comment);

        // ── Resend bookkeeping ──────────────────────────────────────
        // ANY reviewer state → DRAFT is a "Resend to Initiator" (Round-2
        // tester feedback widened this from HOD-only to every reviewer
        // stage). When that happens we:
        //   1. Increment resend_count so the timeline can render
        //      "this record was resent N times" downstream.
        //   2. Re-assign the record to the original raiser via
        //      assignedToId so the existing findActiveForUser query
        //      surfaces the DRAFT in their bell immediately.
        //   3. Stamp the resend comment into approval_comments so the
        //      Initiator opening the record sees the reviewer's reason
        //      without digging through status_history.
        if (newStatus == QmsStatus.DRAFT && current != QmsStatus.REJECTED
                && current != QmsStatus.REOPENED && current != QmsStatus.DRAFT) {
            Integer prior = record.getResendCount() != null ? record.getResendCount() : 0;
            record.setResendCount(prior + 1);
            if (record.getRaisedById() != null) {
                record.setAssignedToId(record.getRaisedById());
                record.setAssignedToName(record.getRaisedByName());
            }
            record.setApprovalComments(comment);
        }

        // Set approval metadata when reaching certain statuses
        if (newStatus == QmsStatus.CLOSED || newStatus == QmsStatus.PENDING_HEAD_QA) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                record.setApprovedByName(auth.getName());
                record.setApprovedAt(LocalDateTime.now());
            }
            if (newStatus == QmsStatus.CLOSED) {
                record.setClosedDate(LocalDate.now());
                record.setApprovalComments(comment);
            }
        }

        // Round-3 R27: when Head QA approves and forwards (PENDING_HEAD_QA →
        // PENDING_VERIFICATION) auto-set the target completion date based on
        // the QA-assigned risk category, IF the field is still null.
        //   • Critical → +365 days
        //   • Major    → +90  days
        //   • Minor    → +30  days
        // We deliberately do nothing when the field is already set so a
        // human-supplied date isn't silently overwritten.
        // Round-3 R28: CC primary forward from PENDING_HEAD_QA now goes
        // through PENDING_ATTACHMENTS, so the auto-target-date applies on
        // that transition too.
        // Round-4 G3: Market Complaint closes directly from PENDING_HEAD_QA
        // without a verification step, so include CLOSED as a target — the
        // auto-target still helps the audit report show "due-vs-actual".
        if (current == QmsStatus.PENDING_HEAD_QA
                && (newStatus == QmsStatus.PENDING_VERIFICATION
                    || newStatus == QmsStatus.PENDING_ATTACHMENTS
                    || newStatus == QmsStatus.CLOSED)
                && record.getTargetCompletionDate() == null && record.getCategory() != null) {
            int days = switch (record.getCategory().trim().toLowerCase()) {
                case "critical" -> 365;
                case "major"    -> 90;
                case "minor"    -> 30;
                default          -> 0;
            };
            if (days > 0) {
                record.setTargetCompletionDate(LocalDate.now().plusDays(days));
                log.info("Auto-set target_completion_date = {} ({} days, category={}) on record id={}",
                        record.getTargetCompletionDate(), days, record.getCategory(), record.getId());
            }
        }

        // Round-3 R28 + Round-4 G4: when CC / Deviation / Incident / CAPA
        // enter PENDING_ATTACHMENTS, auto-create a dept-attachment-request
        // row for each dept comment that flagged action_required = TRUE.
        // The respective dept HOD then uploads the supporting document +
        // remark, and Head QA approves each row before the record can
        // advance to PENDING_VERIFICATION (gated by the
        // requireDeptAttachmentsApproved guard above). Market Complaint
        // has no PENDING_ATTACHMENTS stage so it's excluded.
        boolean supportsAttachmentFanOut =
                record.getRecordType() == com.qms.common.enums.QmsRecordType.CHANGE_CONTROL
             || record.getRecordType() == com.qms.common.enums.QmsRecordType.DEVIATION
             || record.getRecordType() == com.qms.common.enums.QmsRecordType.INCIDENT
             || record.getRecordType() == com.qms.common.enums.QmsRecordType.CAPA;
        if (supportsAttachmentFanOut
                && newStatus == QmsStatus.PENDING_ATTACHMENTS
                && current != QmsStatus.PENDING_ATTACHMENTS) {
            autoSpawnActionRequiredDeptAttachmentRows(record);
        }

        // Round-M (2026-06-27) tester CC-Point-1 · Issues 4 + 9: push a
        // human-readable description into the thread-local audit context
        // so the @Audited interceptor on the calling service method
        // writes something like
        //   "Change Control CC-202606-0021: DRAFT → PENDING_REVIEW ·
        //    Remark: Ready for review"
        // instead of the auto-generated boilerplate
        //   "SUBMIT on ChangeControl:51".
        // The interceptor prefers ctx.getDescription() over the annotation
        // default, so this takes over cleanly with no service-side changes.
        publishAuditDescription(record, current, newStatus, comment);
    }

    /**
     * Round-M audit description builder — see comment inside transition().
     * The resulting string is what a QA auditor sees on the Audit Trail
     * table's Description column, so keep it short, factual, and include
     * the record number, status transition, and the actor's remark.
     */
    private void publishAuditDescription(QmsRecord record,
                                          QmsStatus from,
                                          QmsStatus to,
                                          String comment) {
        String moduleLabel = humanModuleLabel(record.getRecordType());
        String recordRef   = record.getRecordNumber() != null
                ? record.getRecordNumber()
                : ("#" + record.getId());
        String remarkPart  = (comment != null && !comment.isBlank())
                ? " · Remark: " + truncate(comment.trim(), 200)
                : "";
        String desc = String.format("%s %s: %s → %s%s",
                moduleLabel, recordRef, from, to, remarkPart);

        // Truncate defensively — audit_logs.description is VARCHAR(500).
        AuditContextHolder.set(AuditContext.builder()
                .entityType(record.getRecordType().name())
                .entityId(record.getId())
                .description(truncate(desc, 480))
                .build());
    }

    private String humanModuleLabel(com.qms.common.enums.QmsRecordType type) {
        return switch (type) {
            case CHANGE_CONTROL   -> "Change Control";
            case CAPA             -> "CAPA";
            case DEVIATION        -> "Deviation";
            case INCIDENT         -> "Incident";
            case MARKET_COMPLAINT -> "Market Complaint";
            case AUDIT_SCHEDULE   -> "Audit Schedule";
        };
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void autoSpawnActionRequiredDeptAttachmentRows(QmsRecord record) {
        var actionRequiredComments = deptCommentRepository
                .findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByCreatedAtAsc(
                        record.getRecordType(), record.getId())
                .stream()
                .filter(c -> Boolean.TRUE.equals(c.getActionRequired()))
                .toList();
        if (actionRequiredComments.isEmpty()) return;

        var existingRows = deptAttachmentRepository
                .findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByCreatedAtAsc(
                        record.getRecordType(), record.getId());
        var existingOpenDeptIds = new java.util.HashSet<Long>();
        for (var r : existingRows) {
            if (!"APPROVED".equalsIgnoreCase(r.getStatus())) {
                existingOpenDeptIds.add(r.getDepartmentId());
            }
        }

        int created = 0;
        for (var c : actionRequiredComments) {
            if (c.getDepartmentId() == null) continue;
            if (existingOpenDeptIds.contains(c.getDepartmentId())) continue;
            var row = com.qms.module.qms.common.entity.QmsDepartmentAttachment.builder()
                    .recordType(record.getRecordType())
                    .recordId(record.getId())
                    .departmentId(c.getDepartmentId())
                    .departmentName(c.getDepartmentName())
                    .status("PENDING")
                    .build();
            deptAttachmentRepository.save(row);
            created++;
        }
        if (created > 0) {
            log.info("Auto-created {} dept-attachment-request row(s) on {} record id={}",
                    created, record.getRecordType(), record.getId());
        }
    }

    /**
     * Submit — DRAFT → PENDING_REVIEW.
     *
     * Round-L (2026-06-26): the submit shortcut now routes to the new
     * peer-review gate, not directly to HOD. The reviewer (another user
     * in the same department flagged is_dept_reviewer) is responsible
     * for forwarding the record to PENDING_HOD via approve() once they
     * have verified the captured fields.
     */
    public void submit(QmsRecord record, String comment) {
        transition(record, QmsStatus.PENDING_REVIEW, comment);
    }

    /**
     * Approve — advances to the canonical next step for this module.
     * For optional branches, callers must use transition() with an explicit targetStatus.
     */
    public void approve(QmsRecord record, String comment) {
        QmsStatus target = WorkflowTransition
                .primaryApprovalTarget(record.getRecordType(), record.getStatus())
                .orElseThrow(() -> AppException.badRequest(
                        "No primary approval path defined from " + record.getStatus() +
                        " for " + record.getRecordType() + ". Use /transition with an explicit targetStatus."));
        transition(record, target, comment);
    }

    /**
     * Reject — moves to REJECTED from any pending state.
     */
    public void reject(QmsRecord record, String comment) {
        if (!WorkflowTransition.isAllowed(record.getRecordType(), record.getStatus(), QmsStatus.REJECTED)) {
            throw AppException.badRequest(
                    "Cannot reject a record in status " + record.getStatus());
        }
        transition(record, QmsStatus.REJECTED, comment);
        record.setApprovalComments(comment);
    }

    /**
     * Close — moves to CLOSED (only when CLOSED is an allowed next status from current state).
     *
     * Round-2 I1: when closing FROM the Verification stage, every verification-
     * phase column the QA Reviewer is supposed to fill must be populated.
     * Without this guard a reviewer could click "Close Record" on an empty
     * verification form and end up with a closed record that's missing the
     * mandatory verification audit data.
     */
    public void close(QmsRecord record, String comment) {
        if (!WorkflowTransition.isAllowed(record.getRecordType(), record.getStatus(), QmsStatus.CLOSED)) {
            throw AppException.badRequest(
                    "Cannot close a record in status " + record.getStatus() +
                    ". Allowed transitions: " + WorkflowTransition.allowedFrom(record.getRecordType(), record.getStatus()));
        }
        requireVerificationFieldsFilled(record);
        transition(record, QmsStatus.CLOSED, comment);
    }

    private void requireVerificationFieldsFilled(QmsRecord record) {
        // Only enforce when closing from a verification stage. Other
        // closures (e.g. CAPA's PENDING_VERIFICATION_REVIEW → CLOSED) are
        // already gated by their stage's distinct guards.
        QmsStatus s = record.getStatus();
        if (s != QmsStatus.PENDING_VERIFICATION) return;

        List<String> missing = new ArrayList<>();
        if (isBlank(record.getVerificationActionTaken()))   missing.add("Action Taken");
        if (record.getVerificationEffectiveOn() == null)    missing.add("Effective On date");
        // Round-3 R29: Documents Reissue dropped from the verification form
        // and the close-side guard. The narrative captures it instead.
        if (!missing.isEmpty()) {
            throw AppException.badRequest(
                "Cannot close record — the following verification field(s) must be filled first: "
                + String.join(", ", missing) + ".");
        }
        // Round-3 R29: effective/implemented date cannot be in the past.
        if (record.getVerificationEffectiveOn().isBefore(LocalDate.now())) {
            throw AppException.badRequest(
                "Cannot close record — Effective / Implemented On date " +
                record.getVerificationEffectiveOn() + " is before today. Pick today or a future date.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Cancel — moves any non-terminal record to CANCELLED.
     */
    public void cancel(QmsRecord record, String comment) {
        if (record.isTerminal()) {
            throw AppException.badRequest(
                    "Cannot cancel a record that is already " + record.getStatus());
        }
        transition(record, QmsStatus.CANCELLED, comment);
    }

    /**
     * Reopen a closed record — CLOSED → DRAFT.
     */
    public void reopen(QmsRecord record, String comment) {
        if (record.getStatus() != QmsStatus.CLOSED) {
            throw AppException.badRequest("Only CLOSED records can be reopened");
        }
        transition(record, QmsStatus.DRAFT, comment);
        record.setClosedDate(null);
    }

    // ── Validation ─────────────────────────────────────────────

    /**
     * Every workflow action must carry a non-blank comment. This is a 21 CFR
     * Part 11 / GxP requirement — every status change must record an
     * intelligible reason alongside the actor and timestamp.
     */
    private void requireComment(String comment) {
        if (comment == null || comment.isBlank()) {
            throw AppException.badRequest(
                    "Comment is required for every workflow action.");
        }
    }

    /**
     * Enforces the structural role required to drive a record into the given
     * target status. SUPER_ADMIN bypasses every check. Statuses without a
     * mapped position (DRAFT, CANCELLED, REJECTED, REOPENED, optional
     * branches) are not gated here — the graph rules are enough.
     *
     * The source state ({@code from}) is consulted via
     * {@link WorkflowPosition#requiredFor(QmsStatus, QmsStatus)} so loop-back
     * transitions (e.g. PENDING_DEPT_COMMENT → PENDING_QA_REVIEW, owned by
     * the HOD of the commenting dept) work correctly.
     */
    private void requirePosition(QmsRecord record, QmsStatus from, QmsStatus target) {
        WorkflowPosition required = WorkflowPosition.requiredFor(
                record.getRecordType(), from, target);
        if (required == null) return;
        if (orgSecurity.isSuperAdmin()) return;

        boolean ok = switch (required) {
            case ANY_INITIATOR          -> orgSecurity.currentUser().isPresent();
            case DEPT_REVIEWER_OF_RECORD_DEPT ->
                orgSecurity.isCurrentUserDeptReviewerOf(record.getDepartmentId());
            case HOD_OF_RECORD_DEPT     -> orgSecurity.isCurrentUserHodOf(record.getDepartmentId());
            case HOD_OF_COMMENTING_DEPT ->
                // Three accepted actors for source-aware loop-backs out of
                // PENDING_DEPT_COMMENT:
                //   1. The explicitly flagged commenting dept's HOD.
                //   2. The HOD of any dept that has a PENDING comment row
                //      on this record (CC / MC fan-out: multiple depts
                //      pending simultaneously, no single "current" dept).
                //   3. The QA Reviewer / Head — they own the canonical
                //      "advance after all comments are in" transition,
                //      which the completion guard below enforces.
                orgSecurity.isCurrentUserHodOf(record.getCommentingDepartmentId())
                || isCurrentUserHodOfAnyPendingComment(record)
                || orgSecurity.isCurrentUserQaReviewer()
                || orgSecurity.isCurrentUserQaHead();
            case QA_REVIEWER            -> orgSecurity.isCurrentUserQaReviewer()
                                            || orgSecurity.isCurrentUserQaHead();
            case QA_HEAD                -> orgSecurity.isCurrentUserQaHead();
            case RA                     -> orgSecurity.isCurrentUserRa();
            case SITE_HEAD              -> orgSecurity.isCurrentUserSiteHead();
        };

        if (!ok) {
            throw AppException.forbidden(
                    "Your role does not permit moving this record to " + target +
                    ". Required: " + required);
        }
    }

    private boolean isCurrentUserHodOfAnyPendingComment(QmsRecord record) {
        var pendingRows = deptCommentRepository
                .findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByCreatedAtAsc(
                        record.getRecordType(), record.getId())
                .stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                .toList();
        for (var row : pendingRows) {
            if (orgSecurity.isCurrentUserHodOf(row.getDepartmentId())) return true;
        }
        return false;
    }

    /**
     * Block forward-progression out of PENDING_DEPT_COMMENT until every
     * requested department comment has been filled. Without this guard,
     * the QA Reviewer could bypass the cross-functional review entirely
     * by clicking "Approve" the moment they invited the depts.
     *
     * Applies to:
     *   • CHANGE_CONTROL   : PENDING_DEPT_COMMENT → PENDING_RA_REVIEW
     *   • MARKET_COMPLAINT : PENDING_DEPT_COMMENT → PENDING_INVESTIGATION
     *
     * The send-back transitions (e.g. PENDING_DEPT_COMMENT → PENDING_QA_REVIEW
     * for CC / Deviation / Incident) are intentionally NOT gated — a dept
     * HOD spotting a problem must be able to bounce the record without
     * waiting on its peers. For Deviation and Incident the loop-back IS
     * the canonical advance (back to QA Review), so we leave it ungated;
     * QA's onward forward to RA / Site Head / Head QA is gated separately
     * because it doesn't usually need every dept comment to be filled —
     * QA can re-invite a missed dept later if needed.
     */
    private void requireDeptCommentsComplete(QmsRecord record, QmsStatus from, QmsStatus to) {
        if (from != QmsStatus.PENDING_DEPT_COMMENT) return;

        boolean isForward =
                (record.getRecordType() == com.qms.common.enums.QmsRecordType.CHANGE_CONTROL
                    && to == QmsStatus.PENDING_RA_REVIEW)
             || (record.getRecordType() == com.qms.common.enums.QmsRecordType.MARKET_COMPLAINT
                    && to == QmsStatus.PENDING_INVESTIGATION);
        if (!isForward) return;

        long pendingCount = deptCommentRepository
                .countByRecordTypeAndRecordIdAndStatusAndIsDeletedFalse(
                        record.getRecordType(), record.getId(), "PENDING");
        if (pendingCount > 0) {
            String forwardLabel = (to == QmsStatus.PENDING_RA_REVIEW)
                    ? "RA Evaluation" : "QA Investigation";
            throw AppException.badRequest(
                    "Cannot forward to " + forwardLabel + " while " + pendingCount +
                    " department comment(s) are still pending. " +
                    "Each requested department's HOD must complete their comment first.");
        }
    }

    /**
     * Dept-attachment-approval gate — per "All the department Attachment
     * should be Approved" callout on both the Deviation and Incident flow
     * charts. The record cannot advance from PENDING_ATTACHMENTS to
     * PENDING_VERIFICATION until every QmsDepartmentAttachment row on the
     * record has reached APPROVED.
     *
     * Pending or REJECTED rows both block progression — the latter forces
     * Head QA to either flip the row to APPROVED with a note, or send the
     * dept's row back for a re-upload before closure can run.
     */
    private void requireDeptAttachmentsApproved(QmsRecord record, QmsStatus from, QmsStatus to) {
        // Round-3 R28: CHANGE_CONTROL joins Deviation / Incident / CAPA in
        // routing through PENDING_ATTACHMENTS so dept-uploaded supporting
        // documents are gated before Verification.
        boolean applies =
                record.getRecordType() == com.qms.common.enums.QmsRecordType.DEVIATION
             || record.getRecordType() == com.qms.common.enums.QmsRecordType.INCIDENT
             || record.getRecordType() == com.qms.common.enums.QmsRecordType.CAPA
             || record.getRecordType() == com.qms.common.enums.QmsRecordType.CHANGE_CONTROL;
        if (!applies) return;
        if (from != QmsStatus.PENDING_ATTACHMENTS || to != QmsStatus.PENDING_VERIFICATION) return;

        long unapproved = deptAttachmentRepository
                .countByRecordTypeAndRecordIdAndStatusNotAndIsDeletedFalse(
                        record.getRecordType(), record.getId(), "APPROVED");
        if (unapproved > 0) {
            String nextLabel;
            switch (record.getRecordType()) {
                case DEVIATION -> nextLabel = "Investigation Summary";
                case CAPA      -> nextLabel = "Verification/Add";
                default        -> nextLabel = "Verification";
            }
            throw AppException.badRequest(
                    "Cannot move to " + nextLabel + " while " + unapproved +
                    " department attachment(s) are not yet APPROVED. " +
                    "Head QA must approve every department's attachment first.");
        }
    }

    /**
     * 30-day SLA on closure for Deviation and Incident. Mirrors the MC
     * 45-day rule but at 30 days. If closure is attempted past day 30,
     * the engine requires an APPROVED target-date extension.
     */
    private void requireExtensionForLateClose30(QmsRecord record, QmsStatus from, QmsStatus to) {
        boolean applies =
                record.getRecordType() == com.qms.common.enums.QmsRecordType.DEVIATION
             || record.getRecordType() == com.qms.common.enums.QmsRecordType.INCIDENT
             || record.getRecordType() == com.qms.common.enums.QmsRecordType.CAPA;
        if (!applies) return;
        if (to != QmsStatus.CLOSED) return;
        if (record.getCreatedAt() == null) return;

        long daysSinceCreation = ChronoUnit.DAYS.between(
                record.getCreatedAt().toLocalDate(), LocalDate.now());
        if (daysSinceCreation <= 30) return;

        String extStatus = record.getTargetDateExtensionStatus();
        if (!"APPROVED".equalsIgnoreCase(extStatus)) {
            String typeLabel = record.getRecordType().name().replace("_", " ");
            throw AppException.badRequest(
                    "This " + typeLabel + " is " + daysSinceCreation +
                    " days old and cannot be closed without an approved target-date extension. " +
                    "Request an extension from the Target Date Extension panel and have Head QA approve it first.");
        }
    }

    /**
     * Market Complaint 45-day SLA. Per the spec, every MC must reach CLOSED
     * within 45 days of creation. If not, the closure path is blocked until
     * a target-date extension is requested AND approved.
     *
     * The extension lifecycle is already modelled inline on QmsRecord via
     * {@code targetDateExtensionStatus} (PENDING / APPROVED / REJECTED). Here
     * we only enforce the close-side gate; requesting / deciding extensions
     * lives in TargetDateExtensionService.
     */
    private void requireMcExtensionForLateClose(QmsRecord record,
                                                 QmsStatus from,
                                                 QmsStatus to) {
        if (record.getRecordType() != com.qms.common.enums.QmsRecordType.MARKET_COMPLAINT) return;
        if (to != QmsStatus.CLOSED) return;
        if (record.getCreatedAt() == null) return;

        long daysSinceCreation = ChronoUnit.DAYS.between(
                record.getCreatedAt().toLocalDate(), LocalDate.now());
        if (daysSinceCreation <= 45) return;

        String extStatus = record.getTargetDateExtensionStatus();
        if (!"APPROVED".equalsIgnoreCase(extStatus)) {
            throw AppException.badRequest(
                    "This Market Complaint is " + daysSinceCreation +
                    " days old and cannot be closed without an approved target-date extension. " +
                    "Request an extension from the Target Date Extension panel and have Head QA approve it first.");
        }
    }

    /**
     * Returns the deserialized status history for a record.
     */
    public List<StatusHistoryEntry> getHistory(QmsRecord record) {
        return deserializeHistory(record.getStatusHistory());
    }

    // ── Internals ─────────────────────────────────────────────

    private void applyTransition(QmsRecord record, QmsStatus from,
                                  QmsStatus to, String comment) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "SYSTEM";

        List<StatusHistoryEntry> history = deserializeHistory(record.getStatusHistory());
        history.add(StatusHistoryEntry.of(from, to, username, null, comment));
        record.setStatusHistory(serializeHistory(history));
        record.setStatus(to);

        log.debug("QMS workflow: {} record {} transitioned {} → {} by {}",
                record.getRecordType(), record.getRecordNumber(), from, to, username);
    }

    private List<StatusHistoryEntry> deserializeHistory(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return mapper.readValue(json, HISTORY_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Could not parse status history JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String serializeHistory(List<StatusHistoryEntry> history) {
        try {
            return mapper.writeValueAsString(history);
        } catch (JsonProcessingException e) {
            log.error("Could not serialize status history: {}", e.getMessage());
            return "[]";
        }
    }
}
