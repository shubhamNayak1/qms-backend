package com.qms.module.qms.common.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.QmsRecordType;
import com.qms.common.exception.AppException;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.dms.entity.Document;
import com.qms.module.dms.repository.DocumentRepository;
import com.qms.module.org.entity.Department;
import com.qms.module.org.repository.DepartmentRepository;
import com.qms.module.org.service.OrgSecurityService;
import com.qms.module.qms.common.dto.request.QmsDepartmentAttachmentDecision;
import com.qms.module.qms.common.dto.request.QmsDepartmentAttachmentRequest;
import com.qms.module.qms.common.dto.response.QmsDepartmentAttachmentResponse;
import com.qms.module.qms.common.entity.QmsDepartmentAttachment;
import com.qms.module.qms.common.repository.QmsDepartmentAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages the per-department attachment-approval rows that back the
 * {@code PENDING_ATTACHMENTS} stage on Deviation, Incident, CAPA and (soon)
 * Change Control. Mirrors the {@link QmsDepartmentCommentService} shape.
 *
 * Lifecycle of a single row:
 *
 *   PENDING   — Head QA / QA Reviewer invited a department but no upload yet
 *   PENDING   — department uploaded an attachment_ref but Head QA hasn't decided
 *   APPROVED  — Head QA accepted the attachment (final)
 *   REJECTED  — Head QA rejected; the dept must re-upload (row stays PENDING
 *               with the rejection note in decision_note + decided_at filled
 *               so the UI shows the rejection trail)
 *
 * The {@code requireDeptAttachmentsApproved} engine guard blocks the
 * {@code PENDING_ATTACHMENTS → PENDING_VERIFICATION} transition while ANY
 * row is not APPROVED.
 *
 * The polymorphic record_type + record_id pair lets us reuse the same
 * table across CC / Deviation / Incident / CAPA without duplicating code.
 *
 * Each operation is {@code @Audited} so the regulatory trail records
 * who invited / uploaded / approved / rejected each row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QmsDepartmentAttachmentService {

    private final QmsDepartmentAttachmentRepository attachmentRepository;
    private final DepartmentRepository              departmentRepository;
    private final DocumentRepository                documentRepository;
    private final OrgSecurityService                orgSecurity;

    public List<QmsDepartmentAttachmentResponse> list(QmsRecordType recordType, Long recordId) {
        return attachmentRepository
                .findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByCreatedAtAsc(recordType, recordId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * QA Reviewer / Head QA invites a department to upload its attachment.
     * Creates a fresh PENDING row.
     */
    @Audited(action = AuditAction.CREATE, module = AuditModule.QMS,
             entityType = "QmsDepartmentAttachment",
             description = "Invited a department to upload supporting attachment")
    @Transactional
    public QmsDepartmentAttachmentResponse request(QmsRecordType recordType,
                                                    Long recordId,
                                                    QmsDepartmentAttachmentRequest req) {
        if (req.getDepartmentId() == null) {
            throw AppException.badRequest("departmentId is required when inviting a department.");
        }
        Department dept = departmentRepository
                .findByIdAndIsDeletedFalse(req.getDepartmentId())
                .orElseThrow(() -> AppException.notFound("Department", req.getDepartmentId()));

        // Prevent duplicate PENDING invitations for the same dept on the same record.
        boolean alreadyPending = attachmentRepository
                .findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByCreatedAtAsc(recordType, recordId)
                .stream()
                .anyMatch(r -> dept.getId().equals(r.getDepartmentId())
                        && !"APPROVED".equalsIgnoreCase(r.getStatus()));
        if (alreadyPending) {
            throw AppException.badRequest(
                    "An open attachment request already exists for this department on this record.");
        }

        QmsDepartmentAttachment row = QmsDepartmentAttachment.builder()
                .recordType(recordType)
                .recordId(recordId)
                .departmentId(dept.getId())
                .departmentName(dept.getName())
                .status("PENDING")
                .build();

        QmsDepartmentAttachment saved = attachmentRepository.save(row);
        log.info("Dept {} invited to upload attachment on {} {}: row id {}",
                dept.getName(), recordType, recordId, saved.getId());
        return toResponse(saved);
    }

    /**
     * Department uploads / updates the attachment reference for its row.
     * Resets any prior REJECTED decision so Head QA can re-review.
     */
    @Audited(action = AuditAction.UPDATE, module = AuditModule.QMS,
             entityType = "QmsDepartmentAttachment", entityIdArgIndex = 0,
             description = "Department uploaded supporting attachment")
    @Transactional
    public QmsDepartmentAttachmentResponse upload(Long rowId,
                                                   QmsDepartmentAttachmentRequest req) {
        QmsDepartmentAttachment row = requireRow(rowId);
        if ("APPROVED".equalsIgnoreCase(row.getStatus())) {
            throw AppException.badRequest("Cannot edit an APPROVED attachment row.");
        }
        if (req.getAttachmentRef() == null || req.getAttachmentRef().isBlank()) {
            throw AppException.badRequest("attachmentRef is required.");
        }

        row.setAttachmentRef(req.getAttachmentRef().trim());
        row.setAttachmentNote(req.getAttachmentNote());
        // Reset prior decision so Head QA's queue picks it up again.
        row.setStatus("PENDING");
        row.setDecidedById(null);
        row.setDecidedByName(null);
        row.setDecidedAt(null);
        row.setDecisionNote(null);

        QmsDepartmentAttachment saved = attachmentRepository.save(row);
        log.info("Dept attachment row {} uploaded by {}",
                rowId, saved.getDepartmentName());
        return toResponse(saved);
    }

    /**
     * Head QA decides on a row — APPROVED or REJECTED with a decision note.
     */
    @Audited(action = AuditAction.APPROVE, module = AuditModule.QMS,
             entityType = "QmsDepartmentAttachment", entityIdArgIndex = 0,
             description = "Head QA decided a department attachment row")
    @Transactional
    public QmsDepartmentAttachmentResponse decide(Long rowId,
                                                   QmsDepartmentAttachmentDecision decision) {
        QmsDepartmentAttachment row = requireRow(rowId);
        if (row.getAttachmentRef() == null || row.getAttachmentRef().isBlank()) {
            throw AppException.badRequest(
                    "Cannot decide a row that has no attachment reference yet — wait for the department to upload first.");
        }
        if (decision.getApprove() == null) {
            throw AppException.badRequest("approve flag is required (true / false).");
        }
        if (decision.getComment() == null || decision.getComment().isBlank()) {
            throw AppException.badRequest("Decision comment is required.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "SYSTEM";
        Long userId = orgSecurity.currentUser().map(u -> u.getId()).orElse(null);

        row.setStatus(decision.getApprove() ? "APPROVED" : "REJECTED");
        row.setDecidedById(userId);
        row.setDecidedByName(username);
        row.setDecidedAt(LocalDateTime.now());
        row.setDecisionNote(decision.getComment());

        QmsDepartmentAttachment saved = attachmentRepository.save(row);
        log.info("Dept attachment row {} → {} by {}",
                rowId, saved.getStatus(), username);
        return toResponse(saved);
    }

    /**
     * Soft-delete a row (e.g. invited the wrong dept). Allowed only on
     * non-APPROVED rows.
     */
    @Audited(action = AuditAction.DELETE, module = AuditModule.QMS,
             entityType = "QmsDepartmentAttachment", entityIdArgIndex = 0,
             captureNewValue = false,
             description = "Department attachment row removed")
    @Transactional
    public void delete(Long rowId) {
        QmsDepartmentAttachment row = requireRow(rowId);
        if ("APPROVED".equalsIgnoreCase(row.getStatus())) {
            throw AppException.badRequest("Cannot remove an APPROVED attachment row.");
        }
        row.setIsDeleted(true);
        attachmentRepository.save(row);
    }

    // ── Helpers ──────────────────────────────────────────────

    private QmsDepartmentAttachment requireRow(Long id) {
        return attachmentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Department Attachment row", id));
    }

    /**
     * Builds the response with denormalised DMS fields when {@code attachmentRef}
     * parses as a numeric DMS document id. Free-text references (legacy or
     * external-system links) leave the DMS fields null and the UI falls back
     * to the raw {@code attachmentRef} value.
     */
    private QmsDepartmentAttachmentResponse toResponse(QmsDepartmentAttachment r) {
        var b = QmsDepartmentAttachmentResponse.builder()
                .id(r.getId())
                .departmentId(r.getDepartmentId())
                .departmentName(r.getDepartmentName())
                .attachmentRef(r.getAttachmentRef())
                .attachmentNote(r.getAttachmentNote())
                .status(r.getStatus())
                .decidedById(r.getDecidedById())
                .decidedByName(r.getDecidedByName())
                .decidedAt(r.getDecidedAt())
                .decisionNote(r.getDecisionNote())
                .createdAt(r.getCreatedAt())
                .createdBy(r.getCreatedBy());

        // DMS resolution — best-effort, never blocks the response.
        if (r.getAttachmentRef() != null && !r.getAttachmentRef().isBlank()) {
            try {
                Long dmsId = Long.parseLong(r.getAttachmentRef().trim());
                Document doc = documentRepository.findByIdAndIsDeletedFalse(dmsId).orElse(null);
                if (doc != null) {
                    b.dmsDocumentId(doc.getId())
                     .dmsDocumentNumber(doc.getDocNumber())
                     .dmsDocumentTitle(doc.getTitle())
                     .dmsDocumentVersion(doc.getVersion());
                }
            } catch (NumberFormatException ignored) {
                // Free-text reference — leave DMS fields blank.
            }
        }
        return b.build();
    }
}
