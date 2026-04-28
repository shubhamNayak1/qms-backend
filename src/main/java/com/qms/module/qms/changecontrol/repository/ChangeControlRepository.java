package com.qms.module.qms.changecontrol.repository;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.changecontrol.entity.ChangeControl;
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
public interface ChangeControlRepository extends JpaRepository<ChangeControl, Long>, JpaSpecificationExecutor<ChangeControl> {

    Optional<ChangeControl> findByIdAndIsDeletedFalse(Long id);
    Optional<ChangeControl> findByRecordNumberAndIsDeletedFalse(String recordNumber);

    long countByStatusAndIsDeletedFalse(QmsStatus status);

    @Query("SELECT COUNT(c) FROM ChangeControl c WHERE c.isDeleted = false AND c.dueDate < :today AND c.status NOT IN ('CLOSED','CANCELLED')")
    long countOverdue(@Param("today") LocalDate today);

    // ── Notification queries ─────────────────────────────────

    @Query("""
            SELECT c FROM ChangeControl c
            WHERE c.isDeleted = false
              AND (  (c.assignedToId = :userId AND c.status NOT IN ('CLOSED','CANCELLED','REJECTED'))
                  OR (c.raisedById  = :userId AND c.status = 'REJECTED')
                  )
            ORDER BY c.priority DESC NULLS LAST, c.dueDate ASC NULLS LAST
            """)
    List<ChangeControl> findActiveForUser(@Param("userId") Long userId);

    @Query("""
            SELECT c FROM ChangeControl c
            WHERE c.isDeleted = false
              AND c.status IN :statuses
            ORDER BY c.priority DESC NULLS LAST, c.dueDate ASC NULLS LAST
            """)
    List<ChangeControl> findByStatusIn(@Param("statuses") Collection<QmsStatus> statuses);
}
