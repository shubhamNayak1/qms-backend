package com.qms.module.qms.common.repository;

import com.qms.module.qms.common.entity.QmsDepartmentActionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QmsDepartmentActionItemRepository
        extends JpaRepository<QmsDepartmentActionItem, Long> {

    List<QmsDepartmentActionItem>
        findAllByDeptCommentIdAndIsDeletedFalseOrderByCreatedAtAsc(Long deptCommentId);

    Optional<QmsDepartmentActionItem> findByIdAndIsDeletedFalse(Long id);

    /**
     * Non-completed items whose target_date falls on {@code date} — used by
     * the daily reminder scheduler to notify dept HODs about upcoming
     * deadlines. Filters out COMPLETED items and rows already soft-deleted.
     */
    @Query("""
            SELECT a FROM QmsDepartmentActionItem a
            WHERE a.isDeleted = FALSE
              AND a.status <> 'COMPLETED'
              AND a.targetDate = :date
            """)
    List<QmsDepartmentActionItem> findDueOn(@Param("date") LocalDate date);
}
