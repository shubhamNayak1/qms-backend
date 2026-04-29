package com.qms.module.reports.repository;

import com.qms.module.reports.entity.SavedReport;
import com.qms.module.reports.enums.ReportModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {

    Page<SavedReport> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<SavedReport> findByCreatedByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(
            Long userId, Pageable pageable);

    Page<SavedReport> findByModuleAndIsDeletedFalseOrderByCreatedAtDesc(
            ReportModule module, Pageable pageable);
}
