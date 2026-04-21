package com.qms.module.dms.workflow;

import com.qms.common.exception.AppException;
import com.qms.module.dms.entity.Document;
import com.qms.module.dms.entity.DocumentApproval;
import com.qms.module.dms.enums.DocumentStatus;
import com.qms.module.dms.repository.DocumentApprovalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Manages the document approval state machine.
 *
 * Allowed transitions:
 *   DRAFT       → UNDER_REVIEW  (submit)
 *   UNDER_REVIEW → APPROVED     (approve — quorum met)
 *   UNDER_REVIEW → REJECTED     (reject)
 *   APPROVED    → EFFECTIVE     (publish)
 *   REJECTED    → DRAFT         (rework — creates new minor version)
 *   EFFECTIVE   → OBSOLETE      (obsolete)
 *   EFFECTIVE   → SUPERSEDED    (system — on new version upload)
 *   DRAFT       → WITHDRAWN     (withdraw)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentWorkflowService {

    private final DocumentApprovalRepository approvalRepository;

    @Value("${dms.workflow.required-approvers:1}")
    private int requiredApprovers;

    // ── Submit ────────────────────────────────────────────────

    public void submit(Document doc, String comments) {
        assertStatus(doc, DocumentStatus.DRAFT, "submit");
        doc.setStatus(DocumentStatus.UNDER_REVIEW);
        log.info("Document {} v{} submitted for review", doc.getDocNumber(), doc.getVersion());
    }

    // ── Approve ───────────────────────────────────────────────

    public void approve(Document doc, String comments) {
        assertStatus(doc, DocumentStatus.UNDER_REVIEW, "approve");
        Authentication auth = currentAuth();

        // Record this approver's decision
        int cycle = currentReviewCycle(doc);
        DocumentApproval approval = DocumentApproval.builder()
                .document(doc)
                .approverId(extractUserId(auth))
                .approverName(auth != null ? auth.getName() : "SYSTEM")
                .decision(DocumentStatus.APPROVED)
                .comments(comments)
                .decidedAt(LocalDateTime.now())
                .reviewCycle(cycle)
                .build();
        doc.getApprovals().add(approval);

        // Check if quorum is met
        long approvedCount = doc.getApprovals().stream()
                .filter(a -> a.getReviewCycle() == cycle)
                .filter(a -> a.getDecision() == DocumentStatus.APPROVED)
                .count();

        if (approvedCount >= requiredApprovers) {
            doc.setStatus(DocumentStatus.APPROVED);
            doc.setApprovedByName(auth != null ? auth.getName() : "SYSTEM");
            doc.setApprovedAt(LocalDateTime.now());
            doc.setApprovalComments(comments);
            log.info("Document {} v{} APPROVED ({}/{} approvers)",
                    doc.getDocNumber(), doc.getVersion(), approvedCount, requiredApprovers);
        } else {
            log.info("Document {} v{} — {}/{} approvers done, waiting for quorum",
                    doc.getDocNumber(), doc.getVersion(), approvedCount, requiredApprovers);
        }
    }

    // ── Reject ────────────────────────────────────────────────

    public void reject(Document doc, String reason) {
        assertStatus(doc, DocumentStatus.UNDER_REVIEW, "reject");
        Authentication auth = currentAuth();

        int cycle = currentReviewCycle(doc);
        DocumentApproval rejection = DocumentApproval.builder()
                .document(doc)
                .approverId(extractUserId(auth))
                .approverName(auth != null ? auth.getName() : "SYSTEM")
                .decision(DocumentStatus.REJECTED)
                .comments(reason)
                .decidedAt(LocalDateTime.now())
                .reviewCycle(cycle)
                .build();
        doc.getApprovals().add(rejection);

        doc.setStatus(DocumentStatus.REJECTED);
        doc.setRejectionReason(reason);
        log.info("Document {} v{} REJECTED: {}", doc.getDocNumber(), doc.getVersion(), reason);
    }

    // ── Publish / make effective ──────────────────────────────

    public void publish(Document doc, LocalDate effectiveDate) {
        assertStatus(doc, DocumentStatus.APPROVED, "publish");
        doc.setStatus(DocumentStatus.EFFECTIVE);
        doc.setPublishedAt(LocalDateTime.now());
        if (effectiveDate != null) {
            doc.setEffectiveDate(effectiveDate);
        } else if (doc.getEffectiveDate() == null) {
            doc.setEffectiveDate(LocalDate.now());
        }
        log.info("Document {} v{} EFFECTIVE from {}",
                doc.getDocNumber(), doc.getVersion(), doc.getEffectiveDate());
    }

    // ── Obsolete ──────────────────────────────────────────────

    public void obsolete(Document doc, String reason) {
        if (doc.getStatus() != DocumentStatus.EFFECTIVE
                && doc.getStatus() != DocumentStatus.APPROVED) {
            throw AppException.badRequest(
                    "Only EFFECTIVE or APPROVED documents can be made obsolete. Current: "
                    + doc.getStatus());
        }
        doc.setStatus(DocumentStatus.OBSOLETE);
        doc.setObsoletedAt(LocalDateTime.now());
        doc.setApprovalComments(reason);
        log.info("Document {} v{} OBSOLETE: {}", doc.getDocNumber(), doc.getVersion(), reason);
    }

    // ── Supersede (called automatically when a new version is published) ─────

    public void supersede(Document doc) {
        if (doc.getStatus() == DocumentStatus.EFFECTIVE
                || doc.getStatus() == DocumentStatus.APPROVED) {
            doc.setStatus(DocumentStatus.SUPERSEDED);
            log.info("Document {} v{} SUPERSEDED", doc.getDocNumber(), doc.getVersion());
        }
    }

    // ── Withdraw (author pulls before approval) ───────────────

    public void withdraw(Document doc) {
        if (doc.getStatus() != DocumentStatus.DRAFT
                && doc.getStatus() != DocumentStatus.UNDER_REVIEW) {
            throw AppException.badRequest(
                    "Only DRAFT or UNDER_REVIEW documents can be withdrawn. Current: "
                    + doc.getStatus());
        }
        doc.setStatus(DocumentStatus.WITHDRAWN);
        log.info("Document {} v{} WITHDRAWN", doc.getDocNumber(), doc.getVersion());
    }

    // ── Helpers ───────────────────────────────────────────────

    private void assertStatus(Document doc, DocumentStatus required, String operation) {
        if (doc.getStatus() != required) {
            throw AppException.badRequest(String.format(
                    "Cannot %s document in status %s. Required: %s",
                    operation, doc.getStatus(), required));
        }
    }

    private int currentReviewCycle(Document doc) {
        return doc.getApprovals().stream()
                .mapToInt(DocumentApproval::getReviewCycle)
                .max()
                .orElse(1);
    }

    private Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null) return null;
        try {
            Object principal = auth.getPrincipal();
            var method = principal.getClass().getMethod("getId");
            Object id = method.invoke(principal);
            return id instanceof Long l ? l : null;
        } catch (Exception ignored) { return null; }
    }
}
