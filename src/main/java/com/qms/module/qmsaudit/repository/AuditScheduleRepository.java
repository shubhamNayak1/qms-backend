package com.qms.module.qmsaudit.repository;

import com.qms.module.qmsaudit.entity.AuditSchedule;
import com.qms.module.qmsaudit.enums.AuditScheduleStatus;
import com.qms.module.qmsaudit.enums.AuditType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditScheduleRepository
        extends JpaRepository<AuditSchedule, Long>, JpaSpecificationExecutor<AuditSchedule> {

    Optional<AuditSchedule> findByIdAndIsDeletedFalse(Long id);

    Page<AuditSchedule> findByIsDeletedFalseOrderByScheduledDateDesc(Pageable pageable);

    Page<AuditSchedule> findByAuditTypeAndIsDeletedFalseOrderByScheduledDateDesc(
            AuditType type, Pageable pageable);

    Page<AuditSchedule> findByStatusAndIsDeletedFalseOrderByScheduledDateDesc(
            AuditScheduleStatus status, Pageable pageable);

    // Count helpers for dashboard
    long countByIsDeletedFalseAndStatus(AuditScheduleStatus status);
    long countByIsDeletedFalseAndAuditType(AuditType type);

    @Query("SELECT COUNT(a) FROM AuditSchedule a WHERE a.isDeleted = false " +
           "AND a.status = 'PLANNED' AND a.scheduledDate < CURRENT_DATE")
    long countOverdue();
}
