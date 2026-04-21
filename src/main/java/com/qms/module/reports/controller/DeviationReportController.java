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
@RequestMapping("/api/v1/reports/deviations")
@RequiredArgsConstructor
@Tag(name = "Reports — Deviation", description = "Deviation reports with filtering and Excel/PDF export")
@SecurityRequirement(name = "bearerAuth")
public class DeviationReportController {

    private final ReportService reportService;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Deviation summary KPIs, breakdowns, and trend data")
    public ResponseEntity<ApiResponse<ReportSummary>> summary(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.deviationSum(filter));
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Deviation count grouped by status")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byStatus(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.deviationByStatus(filter));
    }

    @GetMapping("/by-priority")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Deviation count grouped by priority")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byPriority(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.deviationByPriority(filter));
    }

    @GetMapping("/by-department")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Deviation count grouped by department")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byDepartment(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.deviationByDept(filter));
    }

    @GetMapping("/by-type")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Deviation count grouped by type — Planned vs Unplanned")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byType(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.deviationByType(filter));
    }

    @GetMapping("/by-process-area")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Deviation count grouped by process area (hotspot analysis)")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byProcessArea(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.deviationByArea(filter));
    }

    @GetMapping("/monthly-trend")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Deviation count per calendar month")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> monthlyTrend(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.deviationMonthly(filter));
    }

    @GetMapping("/table")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(
        summary = "Deviation data table — filterable and paginated",
        description = """
            Sample queries:
            - Unplanned, regulatory-reportable: `?deviationType=Unplanned&regulatoryReportable=true`
            - Specific process area: `?processArea=Filling+Line+3`
            - Overdue only: `?overdueOnly=true`
            """
    )
    public ResponseEntity<ApiResponse<PageResponse<DeviationReportRow>>> table(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.deviationTable(filter));
    }

    @PostMapping("/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Export Deviation report to Excel (.xlsx)")
    public ResponseEntity<byte[]> exportExcel(@Valid @RequestBody ExportRequest req) {
        req.setFormat(ExportRequest.ExportFormat.EXCEL);
        byte[] data = reportService.deviationExport(req);
        return fileResponse(data, "Deviation_Report_" + STAMP.format(LocalDate.now()) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @PostMapping("/export/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Export Deviation report to PDF")
    public ResponseEntity<byte[]> exportPdf(@Valid @RequestBody ExportRequest req) {
        req.setFormat(ExportRequest.ExportFormat.PDF);
        byte[] data = reportService.deviationExport(req);
        return fileResponse(data, "Deviation_Report_" + STAMP.format(LocalDate.now()) + ".pdf", "application/pdf");
    }

    @GetMapping("/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Quick Excel export using URL query params")
    public ResponseEntity<byte[]> quickExcel(@Valid @ModelAttribute ReportFilter filter) {
        ExportRequest req = buildExportReq(filter, ExportRequest.ExportFormat.EXCEL);
        byte[] data = reportService.deviationExport(req);
        return fileResponse(data, "Deviation_Report_" + STAMP.format(LocalDate.now()) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    private ExportRequest buildExportReq(ReportFilter f, ExportRequest.ExportFormat fmt) {
        ExportRequest req = new ExportRequest();
        req.setFormat(fmt); req.setIncludeSummary(true);
        req.setDateFrom(f.getDateFrom()); req.setDateTo(f.getDateTo());
        req.setStatuses(f.getStatuses()); req.setPriorities(f.getPriorities());
        req.setDepartment(f.getDepartment()); req.setOverdueOnly(f.getOverdueOnly());
        req.setSearch(f.getSearch()); req.setDeviationType(f.getDeviationType());
        req.setProcessArea(f.getProcessArea()); req.setSize(50000);
        return req;
    }

    private ResponseEntity<byte[]> fileResponse(byte[] data, String filename, String mime) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType(mime))
                .contentLength(data.length).body(data);
    }
}
