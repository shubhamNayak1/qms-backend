package com.qms.module.qms.common.repository;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.qms.common.entity.QmsRecordAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QmsRecordAttachmentRepository extends JpaRepository<QmsRecordAttachment, Long> {

    Optional<QmsRecordAttachment> findByIdAndIsDeletedFalse(Long id);

    List<QmsRecordAttachment> findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByUploadedAtAsc(
            QmsRecordType recordType, Long recordId);
}
