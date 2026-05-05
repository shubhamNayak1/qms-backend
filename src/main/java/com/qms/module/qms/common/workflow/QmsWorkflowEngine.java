package com.qms.module.qms.common.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.enums.QmsStatus;
import com.qms.common.exception.AppException;
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
        requireComment(comment);
        QmsStatus current = record.getStatus();
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
    }

    /**
     * Submit — DRAFT → PENDING_HOD.
     */
    public void submit(QmsRecord record, String comment) {
        transition(record, QmsStatus.PENDING_HOD, comment);
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
     */
    public void close(QmsRecord record, String comment) {
        if (!WorkflowTransition.isAllowed(record.getRecordType(), record.getStatus(), QmsStatus.CLOSED)) {
            throw AppException.badRequest(
                    "Cannot close a record in status " + record.getStatus() +
                    ". Allowed transitions: " + WorkflowTransition.allowedFrom(record.getRecordType(), record.getStatus()));
        }
        transition(record, QmsStatus.CLOSED, comment);
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
        boolean applies =
                record.getRecordType() == com.qms.common.enums.QmsRecordType.DEVIATION
             || record.getRecordType() == com.qms.common.enums.QmsRecordType.INCIDENT;
        if (!applies) return;
        if (from != QmsStatus.PENDING_ATTACHMENTS || to != QmsStatus.PENDING_VERIFICATION) return;

        long unapproved = deptAttachmentRepository
                .countByRecordTypeAndRecordIdAndStatusNotAndIsDeletedFalse(
                        record.getRecordType(), record.getId(), "APPROVED");
        if (unapproved > 0) {
            String nextLabel = (record.getRecordType() == com.qms.common.enums.QmsRecordType.DEVIATION)
                    ? "Investigation Summary" : "Verification";
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
             || record.getRecordType() == com.qms.common.enums.QmsRecordType.INCIDENT;
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
