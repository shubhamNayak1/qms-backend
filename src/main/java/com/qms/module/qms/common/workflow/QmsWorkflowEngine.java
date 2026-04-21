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
 * Generic workflow engine — handles status transitions for every QMS sub-module.
 *
 * Usage (from any sub-module service):
 * <pre>
 *   workflowEngine.transition(capaRecord, QmsStatus.IN_PROGRESS, "Starting investigation");
 *   workflowEngine.approve(capaRecord, "Approved — root cause verified");
 *   workflowEngine.close(capaRecord, "Corrective actions verified effective");
 * </pre>
 *
 * The engine:
 *  1. Validates the transition against WorkflowTransition rules
 *  2. Updates the entity status field
 *  3. Appends a StatusHistoryEntry to the JSON log
 *  4. Sets appropriate date fields (closedDate, approvedAt)
 *  5. Records who made the change (from SecurityContext)
 *
 * Callers are responsible for persisting the entity after calling transition().
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QmsWorkflowEngine {

    /** Injected Spring bean — picks up JavaTimeModule and other global Jackson config. */
    private final ObjectMapper mapper;
    private static final TypeReference<List<StatusHistoryEntry>> HISTORY_TYPE =
            new TypeReference<>() {};

    // ── Public API ────────────────────────────────────────────

    /**
     * Generic transition — validates and applies any allowed status change.
     */
    public void transition(QmsRecord record, QmsStatus newStatus, String comment) {
        QmsStatus current = record.getStatus();

        if (current == newStatus) {
            throw AppException.badRequest(
                    "Record is already in status " + current);
        }
        if (!WorkflowTransition.isAllowed(current, newStatus)) {
            throw AppException.badRequest(
                    WorkflowTransition.transitionError(current, newStatus));
        }

        applyTransition(record, current, newStatus, comment);
    }

    /**
     * Submit for approval — shorthand for IN_PROGRESS → PENDING_APPROVAL.
     */
    public void submit(QmsRecord record, String comment) {
        transition(record, QmsStatus.PENDING_APPROVAL, comment);
    }

    /**
     * Approve — shorthand for PENDING_APPROVAL → APPROVED.
     * Also sets approvedBy fields from the current security context.
     */
    public void approve(QmsRecord record, String comment) {
        transition(record, QmsStatus.APPROVED, comment);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            record.setApprovedByName(auth.getName());
            record.setApprovedAt(LocalDateTime.now());
        }
        record.setApprovalComments(comment);
    }

    /**
     * Reject — shorthand for PENDING_APPROVAL → REJECTED.
     */
    public void reject(QmsRecord record, String comment) {
        transition(record, QmsStatus.REJECTED, comment);
        record.setApprovalComments(comment);
    }

    /**
     * Close — shorthand for APPROVED → CLOSED.
     * Sets closedDate to today.
     */
    public void close(QmsRecord record, String comment) {
        transition(record, QmsStatus.CLOSED, comment);
        record.setClosedDate(LocalDate.now());
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
     * Reopen a closed record for further action.
     */
    public void reopen(QmsRecord record, String comment) {
        if (record.getStatus() != QmsStatus.CLOSED) {
            throw AppException.badRequest("Only CLOSED records can be reopened");
        }
        transition(record, QmsStatus.REOPENED, comment);
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

        // Append history entry
        List<StatusHistoryEntry> history = deserializeHistory(record.getStatusHistory());
        history.add(StatusHistoryEntry.of(from, to, username, null, comment));
        record.setStatusHistory(serializeHistory(history));

        // Apply the new status
        record.setStatus(to);
        log.debug("QMS workflow: record {} transitioned {} → {} by {}",
                record.getRecordNumber(), from, to, username);
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
