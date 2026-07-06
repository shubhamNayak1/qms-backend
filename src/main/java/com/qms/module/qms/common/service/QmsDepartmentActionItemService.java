package com.qms.module.qms.common.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.exception.AppException;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.org.service.OrgSecurityService;
import com.qms.module.qms.common.dto.request.QmsDepartmentActionItemRequest;
import com.qms.module.qms.common.dto.response.QmsDepartmentActionItemResponse;
import com.qms.module.qms.common.entity.QmsDepartmentActionItem;
import com.qms.module.qms.common.entity.QmsDepartmentComment;
import com.qms.module.qms.common.repository.QmsDepartmentActionItemRepository;
import com.qms.module.qms.common.repository.QmsDepartmentCommentRepository;
import com.qms.module.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Round-N (2026-07-04) tester CC-Point-2 · Issue 6.
 * CRUD service for the atomic action items attached to a dept-comment row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QmsDepartmentActionItemService {

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("PENDING", "IN_PROGRESS", "COMPLETED");

    private final QmsDepartmentActionItemRepository repository;
    private final QmsDepartmentCommentRepository    parentRepository;
    private final OrgSecurityService                orgSecurity;

    // ─── Read ────────────────────────────────────────────────

    public List<QmsDepartmentActionItemResponse> list(Long deptCommentId) {
        requireParent(deptCommentId);
        return repository
                .findAllByDeptCommentIdAndIsDeletedFalseOrderByCreatedAtAsc(deptCommentId)
                .stream().map(this::toResponse).toList();
    }

    // ─── Create ─────────────────────────────────────────────

    @Audited(action = AuditAction.CREATE, module = AuditModule.QMS,
             entityType = "QmsDepartmentActionItem",
             description = "Department action item added")
    @Transactional
    public QmsDepartmentActionItemResponse create(Long deptCommentId,
                                                    QmsDepartmentActionItemRequest req) {
        QmsDepartmentComment parent = requireParent(deptCommentId);
        // The HOD of the parent's targeted dept (or SUPER_ADMIN) may add
        // action items — same authorisation as filling the comment.
        if (!orgSecurity.isSuperAdmin()
                && !orgSecurity.isCurrentUserHodOf(parent.getDepartmentId())) {
            throw AppException.forbidden(
                    "Only the HOD of the requested department can add action items.");
        }
        requireDescription(req);

        QmsDepartmentActionItem row = QmsDepartmentActionItem.builder()
                .deptCommentId(deptCommentId)
                .description(req.getDescription().trim())
                .targetDate(req.getTargetDate())
                .status("PENDING")
                .build();
        return toResponse(repository.save(row));
    }

    // ─── Update ─────────────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.QMS,
             entityType = "QmsDepartmentActionItem", entityIdArgIndex = 0,
             description = "Department action item updated")
    @Transactional
    public QmsDepartmentActionItemResponse update(Long id,
                                                    QmsDepartmentActionItemRequest req) {
        QmsDepartmentActionItem row = requireRow(id);
        QmsDepartmentComment parent = requireParent(row.getDeptCommentId());
        if (!orgSecurity.isSuperAdmin()
                && !orgSecurity.isCurrentUserHodOf(parent.getDepartmentId())) {
            throw AppException.forbidden(
                    "Only the HOD of the requested department can update action items.");
        }

        if (req.getDescription() != null && !req.getDescription().isBlank()) {
            row.setDescription(req.getDescription().trim());
        }
        if (req.getTargetDate() != null) {
            row.setTargetDate(req.getTargetDate());
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            String s = req.getStatus().trim().toUpperCase();
            if (!ALLOWED_STATUSES.contains(s)) {
                throw AppException.badRequest(
                        "Status must be one of " + ALLOWED_STATUSES);
            }
            row.setStatus(s);
            if ("COMPLETED".equals(s) && row.getCompletedAt() == null) {
                row.setCompletedAt(LocalDateTime.now());
                orgSecurity.currentUser().ifPresent(u -> {
                    row.setCompletedById(u.getId());
                    row.setCompletedByName(u.getFullName());
                });
            }
            if (!"COMPLETED".equals(s)) {
                // Re-opening — clear the completion stamp.
                row.setCompletedAt(null);
                row.setCompletedById(null);
                row.setCompletedByName(null);
            }
        }
        return toResponse(repository.save(row));
    }

    // ─── Delete ─────────────────────────────────────────────

    @Audited(action = AuditAction.DELETE, module = AuditModule.QMS,
             entityType = "QmsDepartmentActionItem", entityIdArgIndex = 0,
             captureNewValue = false,
             description = "Department action item removed")
    @Transactional
    public void delete(Long id) {
        QmsDepartmentActionItem row = requireRow(id);
        QmsDepartmentComment parent = requireParent(row.getDeptCommentId());
        if (!orgSecurity.isSuperAdmin()
                && !orgSecurity.isCurrentUserHodOf(parent.getDepartmentId())) {
            throw AppException.forbidden(
                    "Only the HOD of the requested department can remove action items.");
        }
        row.setIsDeleted(true);
        repository.save(row);
    }

    // ─── Helpers ────────────────────────────────────────────

    private QmsDepartmentActionItem requireRow(Long id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Department Action Item", id));
    }

    private QmsDepartmentComment requireParent(Long deptCommentId) {
        return parentRepository.findByIdAndIsDeletedFalse(deptCommentId)
                .orElseThrow(() -> AppException.notFound(
                        "Department Comment", deptCommentId));
    }

    private void requireDescription(QmsDepartmentActionItemRequest req) {
        if (req.getDescription() == null || req.getDescription().isBlank()) {
            throw AppException.badRequest("Description is required for an action item.");
        }
        if (req.getTargetDate() != null && req.getTargetDate().isBefore(LocalDate.now())) {
            throw AppException.badRequest("Target date cannot be in the past.");
        }
    }

    private QmsDepartmentActionItemResponse toResponse(QmsDepartmentActionItem r) {
        return QmsDepartmentActionItemResponse.builder()
                .id(r.getId())
                .deptCommentId(r.getDeptCommentId())
                .description(r.getDescription())
                .targetDate(r.getTargetDate())
                .status(r.getStatus())
                .completedAt(r.getCompletedAt())
                .completedById(r.getCompletedById())
                .completedByName(r.getCompletedByName())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
