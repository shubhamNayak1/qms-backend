package com.qms.module.reports.service;

import com.qms.module.reports.aggregation.ReportQueryService;
import com.qms.module.reports.dto.response.QmsDashboardResponse;
import com.qms.module.reports.export.ExcelExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Slim dashboard service — only the two dashboard endpoints remain here.
 * All dynamic/saved report logic lives in ReportManagerService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportQueryService queryService;
    private final ExcelExporter      excelExporter;

    public QmsDashboardResponse dashboard() {
        return queryService.buildDashboard();
    }

    public byte[] dashboardExcel() {
        QmsDashboardResponse dash = queryService.buildDashboard();
        return excelExporter.exportAggregation(
                dash.getMonthlyOpenTrend(),
                "QMS Dashboard",
                "QMS Executive Dashboard — " + java.time.LocalDate.now());
    }
}
