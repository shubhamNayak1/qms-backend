package com.qms.module.lms.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.lms.dto.response.ComplianceDashboardResponse;
import com.qms.module.lms.service.LmsComplianceService;
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
@RequestMapping("/api/v1/lms/compliance")
@RequiredArgsConstructor
@Tag(name = "LMS — Compliance Dashboard",
     description = "Training compliance KPIs across all programs and departments")
@SecurityRequirement(name = "bearerAuth")
public class LmsComplianceController {

    private final LmsComplianceService complianceService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "Get the LMS compliance dashboard",
        description = """
            Returns real-time compliance metrics across all training programs:

            - **Enrollment breakdown**: ENROLLED / IN_PROGRESS / COMPLETED / FAILED / WAIVED / EXPIRED / OVERDUE
            - **Overall compliance rate**: (COMPLETED + WAIVED) / total non-cancelled enrollments
            - **Program summary**: active programs, mandatory programs
            - **Certificate summary**: active and expired certificate counts
            - **Attention lists**: overdue enrollments, enrollments due within warning window, certificates expiring soon
            - **Compliance by department**: per-department compliance rates

            Used by QA dashboards, audit preparation, and executive reporting.
            """
    )
    public ResponseEntity<ApiResponse<ComplianceDashboardResponse>> getDashboard() {
        return ApiResponse.ok(complianceService.getDashboard());
    }
}
