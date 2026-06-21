package com.qms.module.qms.common.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.QmsRecordType;
import com.qms.common.exception.AppException;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.org.entity.Department;
import com.qms.module.org.repository.DepartmentRepository;
import com.qms.module.org.service.OrgSecurityService;
import com.qms.module.qms.common.dto.request.QmsDepartmentCommentRequest;
import com.qms.module.qms.common.dto.response.QmsDepartmentCommentResponse;
import com.qms.module.qms.common.entity.QmsDepartmentComment;
import com.qms.module.qms.common.entity.QmsRecord;
import com.qms.module.qms.common.repository.QmsDepartmentCommentRepository;
import com.qms.module.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Department-wise comments fan-out — QA routes a record to N departments,
 * each department's HOD fills in their comment, the service tracks
 * status + actor + timestamp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QmsDepartmentCommentService {

    private final QmsDepartmentCommentRepository repository;
    private final DepartmentRepository           departmentRepository;
    private final QmsRecordLookupService         recordLookup;
    private final OrgSecurityService             orgSecurity;

    // ─── Read ────────────────────────────────────────────────

    public List<QmsDepartmentCommentResponse> list(QmsRecordType recordType, Long recordId) {
        recordLookup.findByTypeAndId(recordType, recordId);
        return repository
                .findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByCreatedAtAsc(recordType, recordId)
                .stream().map(this::toResponse).toList();
    }

    // ─── Routing — typically called by QA Reviewer ──────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.QMS,
             entityType = "QmsDepartmentComment",
             description = "Comment requested from a department by QA Reviewer")
    @Transactional
    public QmsDepartmentCommentResponse request(QmsRecordType recordType, Long recordId,
                                                 QmsDepartmentCommentRequest req) {
        recordLookup.findByTypeAndId(recordType, recordId);

        // Allow only QA Reviewer / QA Head / SUPER_ADMIN to route a record for comment.
        if (!orgSecurity.isSuperAdmin()
                && !orgSecurity.isCurrentUserQaReviewer()
                && !orgSecurity.isCurrentUserQaHead()) {
            throw AppException.forbidden(
                    "Only QA Reviewer / QA Head can request a department comment.");
        }

        // Idempotent: if a row for this dept already exists and is still PENDING,
        // return it instead of duplicating.
        var existing = repository
                .findFirstByRecordTypeAndRecordIdAndDepartmentIdAndIsDeletedFalse(
                        recordType, recordId, req.getDepartmentId());
        if (existing.isPresent() && "PENDING".equals(existing.get().getStatus())) {
            return toResponse(existing.get());
        }

        Department dept = departmentRepository.findByIdAndIsDeletedFalse(req.getDepartmentId())
                .orElseThrow(() -> AppException.notFound("Department", req.getDepartmentId()));

        QmsDepartmentComment row = QmsDepartmentComment.builder()
                .recordType(recordType)
                .recordId(recordId)
                .departmentId(dept.getId())
                .departmentName(dept.getName())
                .status("PENDING")
                .build();
        QmsDepartmentComment saved = repository.save(row);
        log.info("Comment requested from dept {} on {} #{}", dept.getCode(), recordType, recordId);
        return toResponse(saved);
    }

    // ─── Filling — by the HOD of the targeted department ────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.QMS,
             entityType = "QmsDepartmentComment", entityIdArgIndex = 0,
             description = "Department HOD filled the requested comment")
    @Transactional
    public QmsDepartmentCommentResponse fill(Long commentRowId, QmsDepartmentCommentRequest req) {
        QmsDepartmentComment row = repository.findByIdAndIsDeletedFalse(commentRowId)
                .orElseThrow(() -> AppException.notFound("QmsDepartmentComment", commentRowId));

        // Position check: actor must be the HOD of the department this row belongs to,
        // or a SUPER_ADMIN override.
        if (!orgSecurity.isSuperAdmin()
                && !orgSecurity.isCurrentUserHodOf(row.getDepartmentId())) {
            throw AppException.forbidden(
                    "Only the HOD of the requested department can fill this comment.");
        }

        if (req.getComment() == null || req.getComment().isBlank()) {
            throw AppException.badRequest("Comment text is required.");
        }

        // Tester feedback (May 2026): if action_required is TRUE, a target_date
        // must be supplied AND it must be on-or-before the parent record's
        // target_completion_date. The UI also enforces the upper bound via the
        // <input type=date max=...> control, but the server is authoritative.
        // Round-3 R23: Target date is OPTIONAL even when Action Required = YES
        // (testers' Round-3 amendment to the Round-2 strict mandatory rule).
        // We still validate it when supplied — strict future + ≤ parent date.
        final boolean actionReq = Boolean.TRUE.equals(req.getActionRequired());
        if (actionReq && req.getTargetDate() != null) {
            if (!req.getTargetDate().isAfter(java.time.LocalDate.now())) {
                throw AppException.badRequest(
                        "Department target date " + req.getTargetDate()
                        + " must be a future date (later than today).");
            }
            QmsRecord parent = recordLookup.findByTypeAndId(row.getRecordType(), row.getRecordId());
            if (parent.getTargetCompletionDate() != null
                    && req.getTargetDate().isAfter(parent.getTargetCompletionDate())) {
                throw AppException.badRequest(
                        "Department target date " + req.getTargetDate()
                        + " must be on or before the parent record's target completion date "
                        + parent.getTargetCompletionDate() + ".");
            }
        }

        User actor = orgSecurity.currentUser().orElse(null);
        row.setComment(req.getComment());
        row.setActionRequired(actionReq);
        row.setTargetDate(actionReq ? req.getTargetDate() : null);
        row.setStatus("COMPLETED");
        row.setDoneAt(LocalDateTime.now());
        if (actor != null) {
            row.setDoneById(actor.getId());
            row.setDoneByName(actor.getFullName());
        }
        return toResponse(repository.save(row));
    }

    // ─── Internals ──────────────────────────────────────────

    private QmsDepartmentCommentResponse toResponse(QmsDepartmentComment c) {
        return QmsDepartmentCommentResponse.builder()
                .id(c.getId())
                .recordType(c.getRecordType())
                .recordId(c.getRecordId())
                .departmentId(c.getDepartmentId())
                .departmentName(c.getDepartmentName())
                .status(c.getStatus())
                .comment(c.getComment())
                .actionRequired(c.getActionRequired())
                .targetDate(c.getTargetDate())
                .doneById(c.getDoneById())
                .doneByName(c.getDoneByName())
                .doneAt(c.getDoneAt())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
