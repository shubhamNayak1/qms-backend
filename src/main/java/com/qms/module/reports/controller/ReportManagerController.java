package com.qms.module.reports.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.reports.dto.request.CreateReportRequest;
import com.qms.module.reports.dto.request.UpdateReportRequest;
import com.qms.module.reports.dto.response.*;
import com.qms.module.reports.entity.SavedReport;
import com.qms.module.reports.enums.ExportFormat;
import com.qms.module.reports.enums.ReportModule;
import com.qms.module.reports.service.ReportInsightService;
import com.qms.module.reports.service.ReportManagerService;
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

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports — Manager", description = "Create, run, download and manage dynamic reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportManagerController {

    private final ReportManagerService reportManagerService;
    private final ReportInsightService reportInsightService;

    // ── 1. Create Report ─────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','TRAINING_MANAGER','AUDITOR')")
    @Operation(summary = "Create a new report",
               description = """
                   Define a report by selecting:
                   - **module** (CAPA, DEVIATION, INCIDENT, CHANGE_CONTROL, COMPLAINT, LMS_ENROLLMENT)
                   - **dateFrom / dateTo** — date range filter on created_at
                   - **dimensions** — fields to GROUP data by (e.g. ["status","department"]). Leave empty for detail/flat mode.
                   - **metrics** — additional columns to display. Leave empty for defaults.
                   - **format** — EXCEL or CSV

                   The report is automatically executed on creation. Use GET /reports/modules/{module}/fields
                   to discover available dimension and metric keys for each module.
                   """)
    public ResponseEntity<ApiResponse<ReportResponse>> create(
            @Valid @RequestBody CreateReportRequest req) {
        return ApiResponse.created("Report created and queued for execution", reportManagerService.create(req));
    }

    // ── 2. Edit Report ────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','TRAINING_MANAGER','AUDITOR')")
    @Operation(summary = "Edit report configuration and re-run",
               description = "Partial update — only send fields you want to change. Re-runs the report automatically with the new config.")
    public ResponseEntity<ApiResponse<ReportResponse>> update(
            @PathVariable Long id,
            @RequestBody UpdateReportRequest req) {
        return ApiResponse.ok(reportManagerService.update(id, req));
    }

    // ── 3. History ────────────────────────────────────────────

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','TRAINING_MANAGER','AUDITOR')")
    @Operation(summary = "Get run history for a report",
               description = "Returns all past execution runs for a report — status, duration, row count, errors.")
    public ResponseEntity<ApiResponse<PageResponse<ReportHistoryResponse>>> history(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reportManagerService.history(id, page, size));
    }

    // ── 4. Disable Report ─────────────────────────────────────

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Disable a report", description = "Prevents the report from being re-run. Existing file is preserved.")
    public ResponseEntity<ApiResponse<ReportResponse>> disable(@PathVariable Long id) {
        return ApiResponse.ok("Report disabled", reportManagerService.disable(id));
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Re-enable a disabled report")
    public ResponseEntity<ApiResponse<ReportResponse>> enable(@PathVariable Long id) {
        return ApiResponse.ok("Report enabled", reportManagerService.enable(id));
    }

    // ── 5. Download ───────────────────────────────────────────

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','TRAINING_MANAGER','AUDITOR')")
    @Operation(summary = "Download the generated report file",
               description = "Downloads the Excel (.xlsx) or CSV file generated during the last run. Returns 400 if report hasn't been run yet.")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        SavedReport report = reportManagerService.getById(id);
        byte[]      data   = reportManagerService.download(id);

        String contentType = report.getFormat() == ExportFormat.CSV
                ? "text/csv"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.getFileName()).build().toString())
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(data.length)
                .body(data);
    }

    // ── 6. Re-Run Report ─────────────────────────────────────

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','TRAINING_MANAGER','AUDITOR')")
    @Operation(summary = "Re-run a report",
               description = "Re-executes the report with the same configuration. Overwrites the previous file. Useful for refreshing data.")
    public ResponseEntity<ApiResponse<ReportResponse>> reRun(@PathVariable Long id) {
        return ApiResponse.ok("Report re-run triggered", reportManagerService.reRun(id));
    }

    // ── 7. List Reports ───────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','TRAINING_MANAGER','AUDITOR')")
    @Operation(summary = "List reports",
               description = """
                   Filter by:
                   - No params → all reports
                   - `?mine=true` → only reports created by the logged-in user
                   - `?module=CAPA` → reports for a specific module
                   """)
    public ResponseEntity<ApiResponse<PageResponse<ReportResponse>>> list(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(required = false)     boolean mine,
            @RequestParam(required = false)     ReportModule module) {
        PageResponse<ReportResponse> result;
        if (module != null) {
            result = reportManagerService.listByModule(module, page, size);
        } else if (mine) {
            result = reportManagerService.listByCurrentUser(page, size);
        } else {
            result = reportManagerService.listAll(page, size);
        }
        return ApiResponse.ok(result);
    }

    // ── 8. Insights ───────────────────────────────────────────

    @GetMapping("/{id}/insights")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','TRAINING_MANAGER','AUDITOR')")
    @Operation(summary = "Get insights for a report",
               description = """
                   Analyses the report data and returns:
                   - **totalRows** — total record count
                   - **topGroups** — top 5 groups by count (grouped reports)
                   - **columnStats** — min/max/avg for numeric columns
                   - **trendDirection** — INCREASING / DECREASING / STABLE
                   - **insights** — list of human-readable insight bullets

                   Note: This re-queries the live data (not the cached file) so results are always fresh.
                   """)
    public ResponseEntity<ApiResponse<ReportInsightResponse>> insights(@PathVariable Long id) {
        SavedReport report = reportManagerService.getById(id);
        return ApiResponse.ok(reportInsightService.getInsights(report));
    }

    // ── Module Field Discovery ─────────────────────────────────

    @GetMapping("/modules/{module}/fields")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','TRAINING_MANAGER','AUDITOR')")
    @Operation(summary = "List available dimensions and metrics for a module",
               description = "Use this to populate the dimension/metric selection UI when creating a report.")
    public ResponseEntity<ApiResponse<ModuleFieldsResponse>> moduleFields(
            @PathVariable ReportModule module) {
        return ApiResponse.ok(reportManagerService.getModuleFields(module));
    }
}
