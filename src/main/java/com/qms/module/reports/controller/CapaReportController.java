package com.qms.module.reports.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.reports.dto.request.ExportRequest;
import com.qms.module.reports.dto.request.ReportFilter;
import com.qms.module.reports.dto.response.*;
import com.qms.module.reports.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/capa")
@RequiredArgsConstructor
@Tag(name = "Reports — CAPA", description = "CAPA status, trend, and aggregation reports with Excel/PDF export")
@SecurityRequirement(name = "bearerAuth")
public class CapaReportController {

    private final ReportService reportService;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── Summary KPI block ─────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "CAPA summary — KPIs, totals, and pre-computed breakdowns",
        description = """
            Returns a single summary object containing:
            - Total, Open, In-Progress, Closed, Overdue, Critical counts
            - Average resolution days
            - Overdue rate (%)
            - Status breakdown, Priority breakdown, Department breakdown, Monthly trend

            Use this as the data source for the CAPA summary card on the main dashboard.
            All filter parameters are optional.
            """
    )
    public ResponseEntity<ApiResponse<ReportSummary>> summary(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.capaSum(filter));
    }

    // ── Aggregation breakdowns (for charts) ───────────────────

    @GetMapping("/by-status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "CAPA count grouped by status",
               description = "Returns label/count/percentage for each status. Use for pie or bar charts.")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byStatus(
            @Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.capaByStatus(filter));
    }

    @GetMapping("/by-priority")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "CAPA count grouped by priority")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byPriority(
            @Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.capaByPriority(filter));
    }

    @GetMapping("/by-department")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "CAPA count grouped by department")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byDepartment(
            @Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.capaByDepartment(filter));
    }

    @GetMapping("/by-source")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "CAPA count grouped by source (Audit / Complaint / Deviation / Internal)")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> bySource(
            @Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.capaBySource(filter));
    }

    @GetMapping("/monthly-trend")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "CAPA count per calendar month (trend line data)",
               description = "Returns YYYY-MM labels ordered ascending. Use for trend line charts.")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> monthlyTrend(
            @Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.capaMonthly(filter));
    }

    @GetMapping("/resolution-by-priority")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(summary = "Average CAPA resolution time in days, grouped by priority")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> resolutionByPriority(
            @Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.capaResolutionByPriority(filter));
    }

    // ── Data table (filterable, paginated) ────────────────────

    @GetMapping("/table")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(
        summary = "CAPA data table — filterable, sortable, paginated",
        description = """
            Returns a paginated list of flat CAPA row DTOs suitable for display in a data grid.

            **Sample queries:**
            - Open critical CAPAs: `?statuses=OPEN&priorities=CRITICAL`
            - Overdue CAPAs: `?overdueOnly=true`
            - Manufacturing department Q1 2024: `?department=Manufacturing&dateFrom=2024-01-01&dateTo=2024-03-31`
            - Text search by title or record number: `?search=sterility`
            """
    )
    public ResponseEntity<ApiResponse<PageResponse<CapaReportRow>>> table(
            @Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.capaTable(filter));
    }

    // ── Export endpoints ──────────────────────────────────────

    @PostMapping("/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(
        summary = "Export CAPA report to Excel (.xlsx)",
        description = """
            Applies the same filters as the table endpoint.
            Returns a downloadable .xlsx file with:
            - Company header and report title
            - Summary statistics block (when includeSummary=true)
            - Styled data table with freeze-pane and auto-filter
            - Overdue rows highlighted in red
            - Maximum 50,000 rows (apply filters to reduce if needed)
            """
    )
    public ResponseEntity<byte[]> exportExcel(@Valid @RequestBody ExportRequest req) {
        req.setFormat(ExportRequest.ExportFormat.EXCEL);
        byte[] data = reportService.capaExport(req);
        String filename = "CAPA_Report_" + STAMP.format(LocalDate.now()) + ".xlsx";
        return fileResponse(data, filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @PostMapping("/export/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(
        summary = "Export CAPA report to PDF",
        description = """
            Returns a landscape A4 PDF with:
            - Company header and report title
            - Summary statistics block
            - Columnar data table with alternating row shading
            - Page N of M footer on every page
            - Maximum 5,000 rows (use Excel for larger exports)
            """
    )
    public ResponseEntity<byte[]> exportPdf(@Valid @RequestBody ExportRequest req) {
        req.setFormat(ExportRequest.ExportFormat.PDF);
        byte[] data = reportService.capaExport(req);
        String filename = "CAPA_Report_" + STAMP.format(LocalDate.now()) + ".pdf";
        return fileResponse(data, filename, "application/pdf");
    }

    // ── Quick-export convenience (GET) ────────────────────────

    @GetMapping("/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Quick Excel export using URL query params (no request body needed)",
               description = "Convenience GET version — applies filter from query params, exports all matching rows.")
    public ResponseEntity<byte[]> quickExportExcel(@Valid @ModelAttribute ReportFilter filter) {
        ExportRequest req = new ExportRequest();
        req.setFormat(ExportRequest.ExportFormat.EXCEL);
        req.setIncludeSummary(true);
        req.setDateFrom(filter.getDateFrom()); req.setDateTo(filter.getDateTo());
        req.setStatuses(filter.getStatuses()); req.setPriorities(filter.getPriorities());
        req.setDepartment(filter.getDepartment()); req.setOverdueOnly(filter.getOverdueOnly());
        req.setSearch(filter.getSearch()); req.setSize(50000);
        byte[] data = reportService.capaExport(req);
        return fileResponse(data, "CAPA_Report_" + STAMP.format(LocalDate.now()) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    // ── Helper ────────────────────────────────────────────────

    private ResponseEntity<byte[]> fileResponse(byte[] data, String filename, String mime) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType(mime))
                .contentLength(data.length)
                .body(data);
    }
}
