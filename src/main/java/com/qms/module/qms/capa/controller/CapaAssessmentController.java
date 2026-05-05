package com.qms.module.qms.capa.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.qms.common.dto.request.QmsCapaAssessmentRequest;
import com.qms.module.qms.common.dto.request.QmsCapaAssessmentReviewRequest;
import com.qms.module.qms.common.dto.response.QmsCapaAssessmentResponse;
import com.qms.module.qms.common.service.QmsCapaAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Post-closure CAPA effectiveness-assessment endpoints. The assessment cycles
 * are auto-seeded by {@link com.qms.module.qms.capa.service.CapaService}
 * when Head QA closes the CAPA; this controller handles the per-cycle
 * fill-and-review traffic that follows.
 *
 *   GET  /api/v1/qms/capas/{capaId}/assessments        — list cycles
 *   PUT  /api/v1/qms/capas/assessments/{rowId}         — dept submits a cycle
 *   POST /api/v1/qms/capas/assessments/{rowId}/review  — QA accepts / rejects
 */
@RestController
@RequestMapping("/api/v1/qms/capa")
@RequiredArgsConstructor
@Tag(name = "CAPA Effectiveness Assessment", description = "Post-closure CAPA effectiveness lifecycle")
@SecurityRequirement(name = "bearerAuth")
public class CapaAssessmentController {

    private final QmsCapaAssessmentService assessmentService;

    @GetMapping("/{capaId}/assessments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List the scheduled effectiveness-assessment cycles for a CAPA")
    public ResponseEntity<ApiResponse<List<QmsCapaAssessmentResponse>>> list(
            @PathVariable Long capaId) {
        return ApiResponse.ok(assessmentService.list(capaId));
    }

    @PutMapping("/assessments/{rowId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Responsible dept submits a single effectiveness-assessment cycle")
    public ResponseEntity<ApiResponse<QmsCapaAssessmentResponse>> submit(
            @PathVariable Long rowId,
            @Valid @RequestBody QmsCapaAssessmentRequest req) {
        return ApiResponse.ok("Assessment submitted", assessmentService.submit(rowId, req));
    }

    @PostMapping("/assessments/{rowId}/review")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "QA Reviewer accepts or rejects a submitted effectiveness-assessment cycle")
    public ResponseEntity<ApiResponse<QmsCapaAssessmentResponse>> review(
            @PathVariable Long rowId,
            @Valid @RequestBody QmsCapaAssessmentReviewRequest req) {
        return ApiResponse.ok("Assessment review recorded",
                assessmentService.review(rowId, req));
    }
}
