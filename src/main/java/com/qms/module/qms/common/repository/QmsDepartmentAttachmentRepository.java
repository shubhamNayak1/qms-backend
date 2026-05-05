package com.qms.module.qms.common.repository;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.qms.common.entity.QmsDepartmentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QmsDepartmentAttachmentRepository
        extends JpaRepository<QmsDepartmentAttachment, Long> {

    List<QmsDepartmentAttachment> findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByCreatedAtAsc(
            QmsRecordType recordType, Long recordId);

    Optional<QmsDepartmentAttachment> findByIdAndIsDeletedFalse(Long id);

    /**
     * Count rows on a record that are NOT in the given status. Used by the
     * closure guard to assert "every dept attachment is APPROVED" before
     * Deviation can move on from PENDING_ATTACHMENTS.
     */
    long countByRecordTypeAndRecordIdAndStatusNotAndIsDeletedFalse(
            QmsRecordType recordType, Long recordId, String status);
}
