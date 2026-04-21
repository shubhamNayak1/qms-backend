package com.qms.module.reports.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.reports.dto.request.ExportRequest;
import com.qms.module.reports.dto.request.ReportFilter;
import com.qms.module.reports.dto.response.AggregationResult;
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
@RequestMapping("/api/v1/reports/users")
@RequiredArgsConstructor
@Tag(name = "Reports — Users", description = "User distribution, role, and activity reports with export")
@SecurityRequirement(name = "bearerAuth")
public class UserReportController {

    private final ReportService reportService;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    @GetMapping("/by-department")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "User count grouped by department",
        description = "Returns the number of active users per department. Use for organisational headcount charts."
    )
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byDepartment() {
        return ApiResponse.ok(reportService.usersByDepartment());
    }

    @GetMapping("/by-role")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "User count grouped by assigned role",
        description = "Returns the number of users per system role (QA_MANAGER, QA_OFFICER, AUDITOR, etc.)"
    )
    public ResponseEntity<ApiResponse<List<AggregationResult>>> byRole() {
        return ApiResponse.ok(reportService.usersByRole());
    }

    @GetMapping("/activity-trend")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "Monthly successful login count (user activity trend)",
        description = """
            Queries the audit_logs table for successful LOGIN events.
            Returns YYYY-MM labels with count for trend line charts.
            Apply date filters to limit the range:
              `?dateFrom=2024-01-01&dateTo=2024-12-31`
            """
    )
    public ResponseEntity<ApiResponse<List<AggregationResult>>> activityTrend(
            @Valid @ModelAttribute ReportFilter filter) {
        return ApiResponse.ok(reportService.userActivity(filter));
    }

    @PostMapping("/export/department/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(summary = "Export 'Users by Department' report to Excel")
    public ResponseEntity<byte[]> exportDeptExcel(@Valid @RequestBody ExportRequest req) {
        req.setFormat(ExportRequest.ExportFormat.EXCEL);
        byte[] data = reportService.usersExportByDepartment(req);
        return fileResponse(data, "Users_By_Dept_" + STAMP.format(LocalDate.now()) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @PostMapping("/export/department/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(summary = "Export 'Users by Department' report to PDF")
    public ResponseEntity<byte[]> exportDeptPdf(@Valid @RequestBody ExportRequest req) {
        req.setFormat(ExportRequest.ExportFormat.PDF);
        byte[] data = reportService.usersExportByDepartment(req);
        return fileResponse(data, "Users_By_Dept_" + STAMP.format(LocalDate.now()) + ".pdf", "application/pdf");
    }

    @PostMapping("/export/role/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(summary = "Export 'Users by Role' report to Excel")
    public ResponseEntity<byte[]> exportRoleExcel(@Valid @RequestBody ExportRequest req) {
        req.setFormat(ExportRequest.ExportFormat.EXCEL);
        byte[] data = reportService.usersExportByRole(req);
        return fileResponse(data, "Users_By_Role_" + STAMP.format(LocalDate.now()) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @PostMapping("/export/role/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(summary = "Export 'Users by Role' report to PDF")
    public ResponseEntity<byte[]> exportRolePdf(@Valid @RequestBody ExportRequest req) {
        req.setFormat(ExportRequest.ExportFormat.PDF);
        byte[] data = reportService.usersExportByRole(req);
        return fileResponse(data, "Users_By_Role_" + STAMP.format(LocalDate.now()) + ".pdf", "application/pdf");
    }

    private ResponseEntity<byte[]> fileResponse(byte[] data, String filename, String mime) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType(mime))
                .contentLength(data.length).body(data);
    }
}
