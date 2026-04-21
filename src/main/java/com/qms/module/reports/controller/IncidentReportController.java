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
@RequestMapping("/api/v1/reports/incidents")
@RequiredArgsConstructor
@Tag(name = "Reports — Incident", description = "Incident safety and quality reports with Excel/PDF export")
@SecurityRequirement(name = "bearerAuth")
public class IncidentReportController {

    private final ReportService reportService;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Incident summary KPIs, breakdowns by severity, type, department, and monthly trend")
    public ResponseEntity<ApiResponse<ReportSummary>> summary(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.incidentSum(filter));
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Incident count grouped by status")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byStatus(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.incidentByStatus(filter));
    }

    @GetMapping("/by-severity")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Incident count grouped by severity — Minor, Major, Critical")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> bySeverity(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.incidentBySeverity(filter));
    }

    @GetMapping("/by-type")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Incident count grouped by type — Safety, Quality, Environmental, Equipment, Personnel")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byType(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.incidentByType(filter));
    }

    @GetMapping("/by-department")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Incident count grouped by department")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byDepartment(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.incidentByDept(filter));
    }

    @GetMapping("/by-location")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Incident count grouped by location (hotspot map data)")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byLocation(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.incidentByLocation(filter));
    }

    @GetMapping("/monthly-trend")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Incident count per calendar month")
    public ResponseEntity<ApiResponse<List<AggregationResult>>> monthlyTrend(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.incidentMonthly(filter));
    }

    @GetMapping("/table")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(
        summary = "Incident data table — filterable and paginated",
        description = """
            Sample queries:
            - Critical safety incidents: `?severity=Critical&incidentType=Safety`
            - Injury-involved incidents: `?injuryInvolved=true`
            - Specific location: `?search=Building+2`
            - Date range: `?dateFrom=2024-01-01&dateTo=2024-06-30`
            """
    )
    public ResponseEntity<ApiResponse<PageResponse<IncidentReportRow>>> table(@Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.incidentTable(filter));
    }

    @PostMapping("/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Export Incident report to Excel (.xlsx)")
    public ResponseEntity<byte[]> exportExcel(@Valid @RequestBody ExportRequest req) {
        req.setFormat(ExportRequest.ExportFormat.EXCEL);
        byte[] data = reportService.incidentExport(req);
        return fileResponse(data, "Incident_Report_" + STAMP.format(LocalDate.now()) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @PostMapping("/export/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Export Incident report to PDF (max 5,000 rows)")
    public ResponseEntity<byte[]> exportPdf(@Valid @RequestBody ExportRequest req) {
        req.setFormat(ExportRequest.ExportFormat.PDF);
        byte[] data = reportService.incidentExport(req);
        return fileResponse(data, "Incident_Report_" + STAMP.format(LocalDate.now()) + ".pdf", "application/pdf");
    }

    @GetMapping("/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    @Operation(summary = "Quick Excel export using URL query params")
    public ResponseEntity<byte[]> quickExcel(@Valid @ModelAttribute ReportFilter filter) {
        ExportRequest req = new ExportRequest();
        req.setFormat(ExportRequest.ExportFormat.EXCEL); req.setIncludeSummary(true);
        req.setDateFrom(filter.getDateFrom()); req.setDateTo(filter.getDateTo());
        req.setStatuses(filter.getStatuses()); req.setPriorities(filter.getPriorities());
        req.setDepartment(filter.getDepartment()); req.setSeverity(filter.getSeverity());
        req.setIncidentType(filter.getIncidentType()); req.setInjuryInvolved(filter.getInjuryInvolved());
        req.setOverdueOnly(filter.getOverdueOnly()); req.setSearch(filter.getSearch()); req.setSize(50000);
        byte[] data = reportService.incidentExport(req);
        return fileResponse(data, "Incident_Report_" + STAMP.format(LocalDate.now()) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    private ResponseEntity<byte[]> fileResponse(byte[] data, String filename, String mime) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType(mime))
                .contentLength(data.length).body(data);
    }
}
