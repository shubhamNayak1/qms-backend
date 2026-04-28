package com.qms.module.lms.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.lms.dto.request.ComplianceReviewRequest;
import com.qms.module.lms.dto.request.ComplianceSubmissionRequest;
import com.qms.module.lms.dto.request.TniRequest;
import com.qms.module.lms.dto.response.ComplianceSubmissionResponse;
import com.qms.module.lms.dto.response.TniResponse;
import com.qms.module.lms.service.ComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lms/enrollments/{enrollmentId}/compliance")
@RequiredArgsConstructor
@Tag(name = "LMS — Compliance", description = "Trainee compliance submission and multi-step review workflow")
@SecurityRequirement(name = "bearerAuth")
public class ComplianceController {

    private final ComplianceService complianceService;

    // ── Step 6: Trainee submits compliance ───────────────────

    @PostMapping("/submit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit compliance evidence (Step 6)",
               description = """
                   Trainee submits after completing the training:
                   - **attachmentStorageKey**: key from the file upload endpoint
                   - **qnaAnswers**: JSON array of Q&A answers if the program has Q&A configured

                   Enrollment moves to PENDING_REVIEW.
                   """)
    public ResponseEntity<ApiResponse<ComplianceSubmissionResponse>> submit(
            @PathVariable Long enrollmentId,
            @Valid @RequestBody ComplianceSubmissionRequest req) {
        return ApiResponse.created("Compliance submitted", complianceService.submit(enrollmentId, req));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get compliance submission for this enrollment")
    public ResponseEntity<ApiResponse<ComplianceSubmissionResponse>> get(
            @PathVariable Long enrollmentId) {
        return ApiResponse.ok(complianceService.getByEnrollment(enrollmentId));
    }

    // ── Step 7a: Coordinator / Trainer review ────────────────

    @PostMapping("/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','COORDINATOR','TRAINER')")
    @Operation(summary = "Coordinator/Trainer reviews compliance (Step 7a — SCHEDULED/SELF)",
               description = """
                   **APPROVED**:
                   - INDUCTION programs → enrollment moves to PENDING_HR_REVIEW
                   - Exam enabled → enrollment moves to IN_PROGRESS (exam via Assessment API)
                   - No exam → enrollment COMPLETED, certificate issued

                   **REJECTED**:
                   - Enrollment moves to FAILED
                   - A new ALLOCATED retraining enrollment is automatically created
                   """)
    public ResponseEntity<ApiResponse<ComplianceSubmissionResponse>> coordinatorReview(
            @PathVariable Long enrollmentId,
            @Valid @RequestBody ComplianceReviewRequest req) {
        return ApiResponse.ok("Compliance reviewed",
                complianceService.coordinatorReview(enrollmentId, req));
    }

    // ── Step 7b: HR review (Induction only) ─────────────────

    @PostMapping("/hr-review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','HR')")
    @Operation(summary = "HR review of induction compliance (Step 7b — INDUCTION only)",
               description = """
                   Only applicable to INDUCTION type programs.
                   Enrollment must be in PENDING_HR_REVIEW status.

                   **APPROVED** → PENDING_QA_APPROVAL
                   **REJECTED** → FAILED + retraining created
                   """)
    public ResponseEntity<ApiResponse<ComplianceSubmissionResponse>> hrReview(
            @PathVariable Long enrollmentId,
            @Valid @RequestBody ComplianceReviewRequest req) {
        return ApiResponse.ok("HR review submitted",
                complianceService.hrReview(enrollmentId, req));
    }

    // ── Step 7c: QA Head approval (Induction only) ──────────

    @PostMapping("/qa-approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "QA Head final approval for induction (Step 7c — INDUCTION only)",
               description = """
                   Only applicable to INDUCTION type programs.
                   Enrollment must be in PENDING_QA_APPROVAL status.

                   **APPROVED** → COMPLETED + certificate issued + TNI auto-generated
                   **REJECTED** → FAILED + retraining created

                   Optionally include TNI data (job description, gaps, recommendations) in the request body.
                   """)
    public ResponseEntity<ApiResponse<ComplianceSubmissionResponse>> qaApprove(
            @PathVariable Long enrollmentId,
            @Valid @RequestBody ComplianceReviewRequest req,
            @RequestBody(required = false) TniRequest tniData) {
        return ApiResponse.ok("QA approval submitted",
                complianceService.qaHeadApprove(enrollmentId, req, tniData));
    }

    // ── Pending reviews (for dashboard) ─────────────────────

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','COORDINATOR','TRAINER','HR')")
    @Operation(summary = "Get all pending compliance submissions across all enrollments")
    public ResponseEntity<ApiResponse<List<ComplianceSubmissionResponse>>> getPending(
            @PathVariable Long enrollmentId) {
        return ApiResponse.ok(complianceService.getPendingReviews());
    }
}
