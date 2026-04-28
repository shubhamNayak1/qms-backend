package com.qms.module.qms.complaint.repository;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.complaint.entity.MarketComplaint;
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
public interface MarketComplaintRepository extends JpaRepository<MarketComplaint, Long>, JpaSpecificationExecutor<MarketComplaint> {

    Optional<MarketComplaint> findByIdAndIsDeletedFalse(Long id);
    Optional<MarketComplaint> findByRecordNumberAndIsDeletedFalse(String recordNumber);

    long countByStatusAndIsDeletedFalse(QmsStatus status);
    long countByReportableToAuthorityTrueAndIsDeletedFalse();

    @Query("SELECT COUNT(mc) FROM MarketComplaint mc WHERE mc.isDeleted = false AND mc.dueDate < :today AND mc.status NOT IN ('CLOSED','CANCELLED')")
    long countOverdue(@Param("today") LocalDate today);

    // ── Notification queries ─────────────────────────────────

    @Query("""
            SELECT mc FROM MarketComplaint mc
            WHERE mc.isDeleted = false
              AND (  (mc.assignedToId = :userId AND mc.status NOT IN ('CLOSED','CANCELLED','REJECTED'))
                  OR (mc.raisedById  = :userId AND mc.status = 'REJECTED')
                  )
            ORDER BY mc.priority DESC NULLS LAST, mc.dueDate ASC NULLS LAST
            """)
    List<MarketComplaint> findActiveForUser(@Param("userId") Long userId);

    @Query("""
            SELECT mc FROM MarketComplaint mc
            WHERE mc.isDeleted = false
              AND mc.status IN :statuses
            ORDER BY mc.priority DESC NULLS LAST, mc.dueDate ASC NULLS LAST
            """)
    List<MarketComplaint> findByStatusIn(@Param("statuses") Collection<QmsStatus> statuses);
}
