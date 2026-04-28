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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviationRepository extends JpaRepository<Deviation, Long>, JpaSpecificationExecutor<Deviation> {

    Optional<Deviation> findByIdAndIsDeletedFalse(Long id);
    Optional<Deviation> findByRecordNumberAndIsDeletedFalse(String recordNumber);

    long countByStatusAndIsDeletedFalse(QmsStatus status);
    long countByPriorityAndIsDeletedFalse(Priority priority);

    @Query("SELECT COUNT(d) FROM Deviation d WHERE d.isDeleted = false AND d.dueDate < :today AND d.status NOT IN ('CLOSED','CANCELLED')")
    long countOverdue(@Param("today") LocalDate today);

    // ── Notification queries ─────────────────────────────────

    @Query("""
            SELECT d FROM Deviation d
            WHERE d.isDeleted = false
              AND (  (d.assignedToId = :userId AND d.status NOT IN ('CLOSED','CANCELLED','REJECTED'))
                  OR (d.raisedById  = :userId AND d.status = 'REJECTED')
                  )
            ORDER BY d.priority DESC NULLS LAST, d.dueDate ASC NULLS LAST
            """)
    List<Deviation> findActiveForUser(@Param("userId") Long userId);

    @Query("""
            SELECT d FROM Deviation d
            WHERE d.isDeleted = false
              AND d.status IN :statuses
            ORDER BY d.priority DESC NULLS LAST, d.dueDate ASC NULLS LAST
            """)
    List<Deviation> findByStatusIn(@Param("statuses") Collection<QmsStatus> statuses);
}
