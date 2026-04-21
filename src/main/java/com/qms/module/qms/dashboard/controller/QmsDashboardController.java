package com.qms.module.qms.dashboard.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.qms.dashboard.dto.QmsDashboardResponse;
import com.qms.module.qms.dashboard.service.QmsDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qms/dashboard")
@RequiredArgsConstructor
@Tag(name = "QMS Dashboard", description = "Unified KPI dashboard across all QMS sub-modules")
@SecurityRequirement(name = "bearerAuth")
public class QmsDashboardController {

    private final QmsDashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "Get the unified QMS dashboard",
        description = """
            Returns real-time KPI counts across all five QMS sub-modules:
            CAPA, Deviation, Incident, Change Control, and Market Complaint.

            Includes:
            - Open / In-Progress / Closed counts per module
            - Overdue counts per module
            - Cross-module status and priority breakdowns
            - Critical/high-severity flags
            - Regulatory reportability flags for complaints
            """
    )
    public ResponseEntity<ApiResponse<QmsDashboardResponse>> getDashboard() {
        return ApiResponse.ok(dashboardService.getDashboard());
    }
}
