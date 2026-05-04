package com.qms.module.qms.common.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.enums.QmsStatus;
import com.qms.common.exception.AppException;
import com.qms.module.org.service.OrgSecurityService;
import com.qms.module.qms.common.entity.QmsRecord;
import com.qms.module.qms.common.repository.QmsDepartmentCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final ObjectMapper                   mapper;
    private final OrgSecurityService             orgSecurity;
    private final QmsDepartmentCommentRepository deptCommentRepository;

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
        // Block forward-progression to RA Review until every QmsDepartmentComment
        // requested for this record is COMPLETED. Otherwise QA Reviewers can
        // bypass the dept fan-out by clicking Approve too early.
        requireDeptCommentsComplete(record, current, newStatus);

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
        WorkflowPosition required = WorkflowPosition.requiredFor(from, target);
        if (required == null) return;
        if (orgSecurity.isSuperAdmin()) return;

        boolean ok = switch (required) {
            case ANY_INITIATOR          -> orgSecurity.currentUser().isPresent();
            case HOD_OF_RECORD_DEPT     -> orgSecurity.isCurrentUserHodOf(record.getDepartmentId());
            case HOD_OF_COMMENTING_DEPT ->
                // Either the explicitly-flagged commenting dept's HOD, OR the
                // HOD of any dept that has a PENDING comment row on this record.
                // The latter handles the standard CC fan-out (where multiple
                // depts have PENDING rows simultaneously) without requiring
                // the engine to track which dept is "current".
                orgSecurity.isCurrentUserHodOf(record.getCommentingDepartmentId())
                || isCurrentUserHodOfAnyPendingComment(record);
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
     * Block PENDING_DEPT_COMMENT → PENDING_RA_REVIEW until every requested
     * department comment has been filled. Without this, the QA Reviewer
     * could bypass the cross-functional review by clicking Approve early.
     */
    private void requireDeptCommentsComplete(QmsRecord record, QmsStatus from, QmsStatus to) {
        if (from != QmsStatus.PENDING_DEPT_COMMENT || to != QmsStatus.PENDING_RA_REVIEW) return;

        long pendingCount = deptCommentRepository
                .countByRecordTypeAndRecordIdAndStatusAndIsDeletedFalse(
                        record.getRecordType(), record.getId(), "PENDING");
        if (pendingCount > 0) {
            throw AppException.badRequest(
                    "Cannot forward to RA Evaluation while " + pendingCount +
                    " department comment(s) are still pending. " +
                    "Each requested department's HOD must complete their comment first.");
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
