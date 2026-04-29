package com.qms.module.reports.repository;

import com.qms.module.reports.entity.ReportRunHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRunHistoryRepository extends JpaRepository<ReportRunHistory, Long> {

    Page<ReportRunHistory> findByReportIdOrderByStartedAtDesc(Long reportId, Pageable pageable);
}
