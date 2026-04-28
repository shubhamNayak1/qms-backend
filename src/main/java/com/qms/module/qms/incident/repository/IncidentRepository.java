package com.qms.module.qms.incident.repository;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.incident.entity.Incident;
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
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByIdAndIsDeletedFalse(Long id);
    Optional<Incident> findByRecordNumberAndIsDeletedFalse(String recordNumber);

    long countByStatusAndIsDeletedFalse(QmsStatus status);
    long countBySeverityAndIsDeletedFalse(String severity);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.isDeleted = false AND i.dueDate < :today AND i.status NOT IN ('CLOSED','CANCELLED')")
    long countOverdue(@Param("today") LocalDate today);

    // ── Notification queries ─────────────────────────────────

    @Query("""
            SELECT i FROM Incident i
            WHERE i.isDeleted = false
              AND (  (i.assignedToId = :userId AND i.status NOT IN ('CLOSED','CANCELLED','REJECTED'))
                  OR (i.raisedById  = :userId AND i.status = 'REJECTED')
                  )
            ORDER BY i.priority DESC NULLS LAST, i.dueDate ASC NULLS LAST
            """)
    List<Incident> findActiveForUser(@Param("userId") Long userId);

    @Query("""
            SELECT i FROM Incident i
            WHERE i.isDeleted = false
              AND i.status IN :statuses
            ORDER BY i.priority DESC NULLS LAST, i.dueDate ASC NULLS LAST
            """)
    List<Incident> findByStatusIn(@Param("statuses") Collection<QmsStatus> statuses);
}
