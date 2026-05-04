package com.qms.module.qms.common.repository;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.qms.common.entity.QmsLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QmsLineItemRepository extends JpaRepository<QmsLineItem, Long> {

    List<QmsLineItem> findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderBySrNoAsc(
            QmsRecordType recordType, Long recordId);

    Optional<QmsLineItem> findByIdAndIsDeletedFalse(Long id);

    long countByRecordTypeAndRecordIdAndIsDeletedFalse(QmsRecordType recordType, Long recordId);
}
