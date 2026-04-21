package com.qms.module.qms.deviation.repository;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.deviation.entity.Deviation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DeviationRepository extends JpaRepository<Deviation, Long>, JpaSpecificationExecutor<Deviation> {

    Optional<Deviation> findByIdAndIsDeletedFalse(Long id);
    Optional<Deviation> findByRecordNumberAndIsDeletedFalse(String recordNumber);

    long countByStatusAndIsDeletedFalse(QmsStatus status);
    long countByPriorityAndIsDeletedFalse(Priority priority);

    @Query("SELECT COUNT(d) FROM Deviation d WHERE d.isDeleted = false AND d.dueDate < :today AND d.status NOT IN ('CLOSED','CANCELLED')")
    long countOverdue(@Param("today") LocalDate today);
}
