package com.qms.module.reports.scheduler;

import com.qms.module.reports.dto.request.ReportFilter;
import com.qms.module.reports.dto.response.QmsDashboardResponse;
import com.qms.module.reports.dto.response.ReportSummary;
import com.qms.module.reports.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Scheduled report generation.
 *
 * Runs only when reports.scheduled.enabled = true (disabled by default).
 * In production, integrate with an email or notification service to send
 * the generated reports to recipients.
 *
 * Current jobs:
 *  - Weekly summary (Monday 06:00): CAPA, Deviation, Incident summaries
 *  - Monthly summary (1st of month 05:00): full cross-module dashboard
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reports.scheduled.enabled", havingValue = "true")
public class ReportScheduler {

    private final ReportService reportService;

    @Value("${reports.export.company-name:QMS Organisation}")
    private String companyName;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy");

    // ── Weekly summary (Monday 06:00) ─────────────────────────

    @Scheduled(cron = "${reports.scheduled.weekly-cron:0 0 6 * * MON}")
    public void weeklyQmsSummary() {
        log.info("Scheduled: Weekly QMS summary report started");

        // Last 7 days
        ReportFilter filter = new ReportFilter();
        filter.setDateFrom(LocalDate.now().minusDays(7));
        filter.setDateTo(LocalDate.now());
        filter.setSize(10000);

        try {
            ReportSummary capaSummary = reportService.capaSum(filter);
            log.info("Weekly CAPA: total={} open={} overdue={} avgDays={}",
                    capaSummary.getTotalRecords(),
                    capaSummary.getOpenCount(),
                    capaSummary.getOverdueCount(),
                    capaSummary.getAvgResolutionDays());

            ReportSummary deviationSummary = reportService.deviationSum(filter);
            log.info("Weekly Deviation: total={} open={} overdue={}",
                    deviationSummary.getTotalRecords(),
                    deviationSummary.getOpenCount(),
                    deviationSummary.getOverdueCount());

            ReportSummary incidentSummary = reportService.incidentSum(filter);
            log.info("Weekly Incident: total={} open={} overdue={}",
                    incidentSummary.getTotalRecords(),
                    incidentSummary.getOpenCount(),
                    incidentSummary.getOverdueCount());

            // TODO: send summaries via email / notification service
            // emailService.sendWeeklySummary(capaSummary, deviationSummary, incidentSummary);

        } catch (Exception e) {
            log.error("Weekly QMS summary report failed: {}", e.getMessage(), e);
        }
    }

    // ── Monthly full dashboard (1st of month 05:00) ───────────

    @Scheduled(cron = "${reports.scheduled.monthly-cron:0 0 5 1 * *}")
    public void monthlyDashboardReport() {
        String month = MONTH_FMT.format(LocalDate.now().minusDays(1)); // previous month label
        log.info("Scheduled: Monthly QMS dashboard report — {}", month);

        try {
            QmsDashboardResponse dashboard = reportService.dashboard();

            log.info("Monthly Dashboard: " +
                    "capaOpen={} deviationOpen={} incidentOpen={} " +
                    "ccOpen={} complaintOpen={} totalOverdue={}",
                    dashboard.getCapaOpen(),
                    dashboard.getDeviationOpen(),
                    dashboard.getIncidentOpen(),
                    dashboard.getChangeControlOpen(),
                    dashboard.getComplaintOpen(),
                    dashboard.getTotalOverdueRecords());

            // Optional: generate Excel and attach to email
            // byte[] excel = excelExporter.exportAggregation(
            //     dashboard.getMonthlyOpenTrend(), "Monthly Dashboard", month);
            // emailService.sendMonthlyDashboard(month, dashboard, excel);

        } catch (Exception e) {
            log.error("Monthly dashboard report failed: {}", e.getMessage(), e);
        }
    }
}
