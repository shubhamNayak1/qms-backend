package com.qms.module.reports.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.reports.dto.request.ExportRequest;
import com.qms.module.reports.dto.response.QmsDashboardResponse;
import com.qms.module.reports.export.ExcelExporter;
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

@RestController
@RequestMapping("/api/v1/reports/dashboard")
@RequiredArgsConstructor
@Tag(name = "Reports — Dashboard",
     description = "Unified QMS executive dashboard — aggregates all modules into one API response")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final ReportService  reportService;
    private final ExcelExporter  excelExporter;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "Unified QMS executive dashboard",
        description = """
            Single endpoint aggregating KPIs from all five QMS modules in one call:
            - **CAPA**: open, in-progress, pending-approval, closed, overdue, critical
            - **Deviation**: open, in-progress, closed, overdue, regulatory-reportable count
            - **Incident**: open, in-progress, closed, overdue, critical severity, injury-involved
            - **Change Control**: open, pending-approval, closed, overdue
            - **Market Complaint**: open, in-progress, closed, overdue, reportable-to-authority
            - **Cross-module**: total open, total overdue, monthly open trend (6 months)
            - **Department breakdown**: top departments by open record count
            - **Average resolution days** per module

            **Caching**: Results are cached for 5 minutes (`reports.cache.dashboard-ttl-seconds`).
            Call this endpoint on page load to populate the main executive dashboard.

            **Permissions**: SUPER_ADMIN, QA_MANAGER, AUDITOR
            """
    )
    public ResponseEntity<ApiResponse<QmsDashboardResponse>> getDashboard() {
        return ApiResponse.ok(reportService.dashboard());
    }

    @PostMapping("/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "Export the dashboard summary to Excel",
        description = """
            Exports the current dashboard KPIs to a multi-sheet .xlsx file:
            - **Sheet 1**: KPI overview (module-by-module counts)
            - **Sheet 2**: Monthly open trend (CAPA + Deviation + Incident combined)
            - **Sheet 3**: Open records by department

            No row data is exported — this is a KPI summary document only.
            Use the per-module `/export/excel` endpoints for detailed row data.
            """
    )
    public ResponseEntity<byte[]> exportExcel(@Valid @RequestBody(required = false) ExportRequest req) {
        QmsDashboardResponse dashboard = reportService.dashboard();
        // Export the cross-module monthly trend as an aggregation sheet
        byte[] data = excelExporter.exportAggregation(
                dashboard.getMonthlyOpenTrend(),
                "QMS Dashboard",
                "QMS Executive Dashboard — " + LocalDate.now()
        );
        String filename = "QMS_Dashboard_" + STAMP.format(LocalDate.now()) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(data.length)
                .body(data);
    }
}
