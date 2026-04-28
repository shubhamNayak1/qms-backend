package com.qms.module.qms.capa.repository;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.capa.entity.Capa;
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
public interface CapaRepository extends JpaRepository<Capa, Long>, JpaSpecificationExecutor<Capa> {

    Optional<Capa> findByRecordNumberAndIsDeletedFalse(String recordNumber);

    Optional<Capa> findByIdAndIsDeletedFalse(Long id);

    boolean existsByRecordNumberAndIsDeletedFalse(String recordNumber);

    // ── Dashboard counters ───────────────────────────────────

    long countByStatusAndIsDeletedFalse(QmsStatus status);

    long countByPriorityAndIsDeletedFalse(Priority priority);

    @Query("SELECT COUNT(c) FROM Capa c WHERE c.isDeleted = false AND c.dueDate < :today AND c.status NOT IN ('CLOSED','CANCELLED')")
    long countOverdue(@Param("today") LocalDate today);

    // ── Notification queries ─────────────────────────────────

    /** Active CAPAs assigned to a user, plus any the user raised that were REJECTED. */
    @Query("""
            SELECT c FROM Capa c
            WHERE c.isDeleted = false
              AND (  (c.assignedToId = :userId AND c.status NOT IN ('CLOSED','CANCELLED','REJECTED'))
                  OR (c.raisedById  = :userId AND c.status = 'REJECTED')
                  )
            ORDER BY c.priority DESC NULLS LAST, c.dueDate ASC NULLS LAST
            """)
    List<Capa> findActiveForUser(@Param("userId") Long userId);

    /** CAPAs in any of the supplied statuses — used by managers to find items awaiting approval. */
    @Query("""
            SELECT c FROM Capa c
            WHERE c.isDeleted = false
              AND c.status IN :statuses
            ORDER BY c.priority DESC NULLS LAST, c.dueDate ASC NULLS LAST
            """)
    List<Capa> findByStatusIn(@Param("statuses") Collection<QmsStatus> statuses);

    // ── Upcoming effectiveness checks ────────────────────────
    @Query("""
            SELECT c FROM Capa c
            WHERE c.isDeleted = false
              AND c.effectivenessCheckDate BETWEEN :from AND :to
              AND c.status = 'CLOSED'
              AND c.isEffective IS NULL
            """)
    List<Capa> findPendingEffectivenessChecks(@Param("from") LocalDate from,
                                               @Param("to")   LocalDate to);
}
