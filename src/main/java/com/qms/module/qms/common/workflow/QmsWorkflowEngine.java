package com.qms.module.qms.common.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.enums.QmsStatus;
import com.qms.common.exception.AppException;
import com.qms.module.qms.common.entity.QmsRecord;
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

    private final ObjectMapper mapper;
    private static final TypeReference<List<StatusHistoryEntry>> HISTORY_TYPE =
            new TypeReference<>() {};

    // ── Public API ────────────────────────────────────────────

    /**
     * Generic transition — validates per-module rules and applies the status change.
     */
    public void transition(QmsRecord record, QmsStatus newStatus, String comment) {
        QmsStatus current = record.getStatus();
        if (current == newStatus) {
            throw AppException.badRequest("Record is already in status " + current);
        }
        if (!WorkflowTransition.isAllowed(record.getRecordType(), current, newStatus)) {
            throw AppException.badRequest(
                    WorkflowTransition.transitionError(record.getRecordType(), current, newStatus));
        }
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
