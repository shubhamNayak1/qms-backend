package com.qms.module.qms.common.repository;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.qms.common.entity.QmsDepartmentComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QmsDepartmentCommentRepository
        extends JpaRepository<QmsDepartmentComment, Long> {

    List<QmsDepartmentComment> findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByCreatedAtAsc(
            QmsRecordType recordType, Long recordId);

    Optional<QmsDepartmentComment> findByIdAndIsDeletedFalse(Long id);

    Optional<QmsDepartmentComment> findFirstByRecordTypeAndRecordIdAndDepartmentIdAndIsDeletedFalse(
            QmsRecordType recordType, Long recordId, Long departmentId);

    long countByRecordTypeAndRecordIdAndStatusAndIsDeletedFalse(
            QmsRecordType recordType, Long recordId, String status);
}
