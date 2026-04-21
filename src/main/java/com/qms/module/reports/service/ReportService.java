package com.qms.module.reports.service;

import com.qms.common.response.PageResponse;
import com.qms.module.reports.aggregation.ReportQueryService;
import com.qms.module.reports.dto.request.ExportRequest;
import com.qms.module.reports.dto.request.ReportFilter;
import com.qms.module.reports.dto.response.*;
import com.qms.module.reports.export.ExcelExporter;
import com.qms.module.reports.export.PdfExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates report queries and export generation.
 *
 * Every public method in this service follows the pattern:
 *   1. Delegate data fetch to ReportQueryService (native SQL)
 *   2. For table endpoints: page-slice the in-memory list and return PageResponse
 *   3. For export endpoints: stream to ExcelExporter or PdfExporter
 *
 * Note on pagination: The query service fetches all matching rows
 * (respecting filter), and pagination is applied in-memory here.
 * For very large tables consider adding LIMIT/OFFSET to the native queries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportQueryService queryService;
    private final ExcelExporter      excelExporter;
    private final PdfExporter        pdfExporter;

    // ════════════════════════════════════════════════════════
    //  CAPA REPORTS
    // ════════════════════════════════════════════════════════

    public ReportSummary capaSum(ReportFilter f)              { return queryService.capaSummary(f); }
    public List<AggregationResult> capaByStatus(ReportFilter f)    { return queryService.capaByStatus(f); }
    public List<AggregationResult> capaByPriority(ReportFilter f)  { return queryService.capaByPriority(f); }
    public List<AggregationResult> capaByDepartment(ReportFilter f){ return queryService.capaByDepartment(f); }
    public List<AggregationResult> capaBySource(ReportFilter f)    { return queryService.capaBySource(f); }
    public List<AggregationResult> capaMonthly(ReportFilter f)     { return queryService.capaMonthlyTrend(f); }
    public List<AggregationResult> capaResolutionByPriority(ReportFilter f) {
        return queryService.capaAvgResolutionByPriority(f);
    }

    public PageResponse<CapaReportRow> capaTable(ReportFilter f) {
        List<CapaReportRow> all = queryService.capaRows(f);
        return pageSlice(all, f.getPage(), f.getSize());
    }

    public byte[] capaExport(ExportRequest req) {
        String title = req.getReportTitle() != null ? req.getReportTitle() : "CAPA Status Report";
        ReportSummary summary = req.isIncludeSummary() ? queryService.capaSummary(req) : null;
        List<CapaReportRow> rows = queryService.capaRows(req);
        log.info("CAPA export: format={} rows={}", req.getFormat(), rows.size());
        return req.getFormat() == ExportRequest.ExportFormat.EXCEL
                ? excelExporter.exportCapa(rows, summary, title)
                : pdfExporter.exportCapa(rows, summary, title);
    }

    // ════════════════════════════════════════════════════════
    //  DEVIATION REPORTS
    // ════════════════════════════════════════════════════════

    public ReportSummary deviationSum(ReportFilter f)                 { return queryService.deviationSummary(f); }
    public List<AggregationResult> deviationByStatus(ReportFilter f)  { return queryService.deviationByStatus(f); }
    public List<AggregationResult> deviationByPriority(ReportFilter f){ return queryService.deviationByPriority(f); }
    public List<AggregationResult> deviationByDept(ReportFilter f)    { return queryService.deviationByDepartment(f); }
    public List<AggregationResult> deviationByType(ReportFilter f)    { return queryService.deviationByType(f); }
    public List<AggregationResult> deviationByArea(ReportFilter f)    { return queryService.deviationByProcessArea(f); }
    public List<AggregationResult> deviationMonthly(ReportFilter f)   { return queryService.deviationMonthlyTrend(f); }

    public PageResponse<DeviationReportRow> deviationTable(ReportFilter f) {
        return pageSlice(queryService.deviationRows(f), f.getPage(), f.getSize());
    }

    public byte[] deviationExport(ExportRequest req) {
        String title = req.getReportTitle() != null ? req.getReportTitle() : "Deviation Report";
        ReportSummary summary = req.isIncludeSummary() ? queryService.deviationSummary(req) : null;
        List<DeviationReportRow> rows = queryService.deviationRows(req);
        log.info("Deviation export: format={} rows={}", req.getFormat(), rows.size());
        return req.getFormat() == ExportRequest.ExportFormat.EXCEL
                ? excelExporter.exportDeviation(rows, summary, title)
                : pdfExporter.exportDeviation(rows, summary, title);
    }

    // ════════════════════════════════════════════════════════
    //  INCIDENT REPORTS
    // ════════════════════════════════════════════════════════

    public ReportSummary incidentSum(ReportFilter f)                  { return queryService.incidentSummary(f); }
    public List<AggregationResult> incidentByStatus(ReportFilter f)   { return queryService.incidentByStatus(f); }
    public List<AggregationResult> incidentBySeverity(ReportFilter f) { return queryService.incidentBySeverity(f); }
    public List<AggregationResult> incidentByDept(ReportFilter f)     { return queryService.incidentByDepartment(f); }
    public List<AggregationResult> incidentByType(ReportFilter f)     { return queryService.incidentByType(f); }
    public List<AggregationResult> incidentByLocation(ReportFilter f) { return queryService.incidentByLocation(f); }
    public List<AggregationResult> incidentMonthly(ReportFilter f)    { return queryService.incidentMonthlyTrend(f); }

    public PageResponse<IncidentReportRow> incidentTable(ReportFilter f) {
        return pageSlice(queryService.incidentRows(f), f.getPage(), f.getSize());
    }

    public byte[] incidentExport(ExportRequest req) {
        String title = req.getReportTitle() != null ? req.getReportTitle() : "Incident Report";
        ReportSummary summary = req.isIncludeSummary() ? queryService.incidentSummary(req) : null;
        List<IncidentReportRow> rows = queryService.incidentRows(req);
        log.info("Incident export: format={} rows={}", req.getFormat(), rows.size());
        return req.getFormat() == ExportRequest.ExportFormat.EXCEL
                ? excelExporter.exportIncident(rows, summary, title)
                : pdfExporter.exportIncident(rows, summary, title);
    }

    // ════════════════════════════════════════════════════════
    //  USER REPORTS
    // ════════════════════════════════════════════════════════

    public List<AggregationResult> usersByDepartment()   { return queryService.usersByDepartment(); }
    public List<AggregationResult> usersByRole()         { return queryService.usersByRole(); }
    public List<AggregationResult> userActivity(ReportFilter f) { return queryService.userActivityTrend(f); }

    public byte[] usersExportByDepartment(ExportRequest req) {
        List<AggregationResult> data = queryService.usersByDepartment();
        String title = req.getReportTitle() != null ? req.getReportTitle() : "Users by Department";
        log.info("Users-dept export: format={}", req.getFormat());
        return req.getFormat() == ExportRequest.ExportFormat.EXCEL
                ? excelExporter.exportAggregation(data, "Users by Department", title)
                : pdfExporter.exportAggregation(data, title);
    }

    public byte[] usersExportByRole(ExportRequest req) {
        List<AggregationResult> data = queryService.usersByRole();
        String title = req.getReportTitle() != null ? req.getReportTitle() : "Users by Role";
        return req.getFormat() == ExportRequest.ExportFormat.EXCEL
                ? excelExporter.exportAggregation(data, "Users by Role", title)
                : pdfExporter.exportAggregation(data, title);
    }

    // ════════════════════════════════════════════════════════
    //  UNIFIED DASHBOARD
    // ════════════════════════════════════════════════════════

    public QmsDashboardResponse dashboard() {
        return queryService.buildDashboard();
    }

    // ── Pagination helper ─────────────────────────────────────

    private <T> PageResponse<T> pageSlice(List<T> all, int page, int size) {
        int total   = all.size();
        int from    = Math.min(page * size, total);
        int to      = Math.min(from + size, total);
        List<T> sub = all.subList(from, to);
        return PageResponse.of(new PageImpl<>(sub, PageRequest.of(page, size), total));
    }
}
