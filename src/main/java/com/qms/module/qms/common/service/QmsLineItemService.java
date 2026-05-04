package com.qms.module.qms.common.service;

import com.qms.common.enums.QmsRecordType;
import com.qms.common.exception.AppException;
import com.qms.module.org.service.OrgSecurityService;
import com.qms.module.qms.common.dto.request.QmsLineItemRequest;
import com.qms.module.qms.common.dto.response.QmsLineItemResponse;
import com.qms.module.qms.common.entity.QmsLineItem;
import com.qms.module.qms.common.repository.QmsLineItemRepository;
import com.qms.module.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * CRUD for the "Existing System / Proposed System / Justification" rows
 * that attach to every QMS record. Shared across all 5 sub-modules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QmsLineItemService {

    private final QmsLineItemRepository    repository;
    private final QmsRecordLookupService   recordLookup;
    private final OrgSecurityService       orgSecurity;

    // ─────────────────────────────────────────────────────────
    //  Read
    // ─────────────────────────────────────────────────────────

    public List<QmsLineItemResponse> list(QmsRecordType recordType, Long recordId) {
        recordLookup.findByTypeAndId(recordType, recordId);  // existence check
        return repository
                .findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderBySrNoAsc(recordType, recordId)
                .stream().map(this::toResponse).toList();
    }

    // ─────────────────────────────────────────────────────────
    //  Create / Update / Delete
    // ─────────────────────────────────────────────────────────

    @Transactional
    public QmsLineItemResponse create(QmsRecordType recordType, Long recordId,
                                       QmsLineItemRequest req) {
        recordLookup.findByTypeAndId(recordType, recordId);

        User actor = orgSecurity.currentUser().orElse(null);

        // Auto-assign Sr No based on existing items.
        long existingCount = repository
                .countByRecordTypeAndRecordIdAndIsDeletedFalse(recordType, recordId);

        QmsLineItem item = QmsLineItem.builder()
                .recordType(recordType)
                .recordId(recordId)
                .srNo((int) existingCount + 1)
                .existingSystem(req.getExistingSystem())
                .proposedSystem(req.getProposedSystem())
                .justification(req.getJustification())
                .proposedById(actor != null ? actor.getId() : null)
                .proposedByName(actor != null ? actor.getFullName() : null)
                .proposedDate(req.getProposedDate() != null ? req.getProposedDate() : LocalDate.now())
                .status(req.getStatus())
                .remark(req.getRemark())
                .build();
        QmsLineItem saved = repository.save(item);
        log.info("Line item created on {} #{}", recordType, recordId);
        return toResponse(saved);
    }

    @Transactional
    public QmsLineItemResponse update(Long lineItemId, QmsLineItemRequest req) {
        QmsLineItem item = require(lineItemId);
        if (req.getExistingSystem() != null) item.setExistingSystem(req.getExistingSystem());
        if (req.getProposedSystem() != null) item.setProposedSystem(req.getProposedSystem());
        if (req.getJustification()  != null) item.setJustification(req.getJustification());
        if (req.getProposedDate()   != null) item.setProposedDate(req.getProposedDate());
        if (req.getStatus()         != null) {
            item.setStatus(req.getStatus());
            User actor = orgSecurity.currentUser().orElse(null);
            if (actor != null && "COMPLETED".equalsIgnoreCase(req.getStatus())) {
                item.setCheckedById(actor.getId());
                item.setCheckedByName(actor.getFullName());
            }
        }
        if (req.getRemark()         != null) item.setRemark(req.getRemark());
        return toResponse(repository.save(item));
    }

    @Transactional
    public void delete(Long lineItemId) {
        QmsLineItem item = require(lineItemId);
        item.setIsDeleted(true);
        repository.save(item);
    }

    // ─────────────────────────────────────────────────────────
    //  Internals
    // ─────────────────────────────────────────────────────────

    private QmsLineItem require(Long id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("QmsLineItem", id));
    }

    private QmsLineItemResponse toResponse(QmsLineItem i) {
        return QmsLineItemResponse.builder()
                .id(i.getId())
                .recordType(i.getRecordType())
                .recordId(i.getRecordId())
                .srNo(i.getSrNo())
                .existingSystem(i.getExistingSystem())
                .proposedSystem(i.getProposedSystem())
                .justification(i.getJustification())
                .proposedById(i.getProposedById())
                .proposedByName(i.getProposedByName())
                .proposedDate(i.getProposedDate())
                .status(i.getStatus())
                .remark(i.getRemark())
                .checkedById(i.getCheckedById())
                .checkedByName(i.getCheckedByName())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}
