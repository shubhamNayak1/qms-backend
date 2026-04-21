package com.qms.module.lms.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.lms.dto.request.AssessmentAnswerRequest;
import com.qms.module.lms.dto.response.AssessmentAttemptResponse;
import com.qms.module.lms.service.AssessmentService;
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
@RequestMapping("/api/v1/lms/enrollments/{enrollmentId}/assessment")
@RequiredArgsConstructor
@Tag(name = "LMS — Assessments", description = "Take and grade training assessments")
@SecurityRequirement(name = "bearerAuth")
public class AssessmentController {

    private final AssessmentService assessmentService;

    @PostMapping("/start")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Start a new assessment attempt",
               description = """
                   Creates a new attempt record and returns it.
                   The attempt is IN_PROGRESS until submitted.
                   Only one active attempt is allowed at a time — submit before starting again.
                   Blocked when the maximum attempt limit is reached.
                   """)
    public ResponseEntity<ApiResponse<AssessmentAttemptResponse>> start(
            @PathVariable Long enrollmentId) {
        return ApiResponse.created("Assessment started",
                assessmentService.startAttempt(enrollmentId));
    }

    @PostMapping("/submit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit assessment answers for grading",
               description = """
                   Provide a map of `questionId → answer`.
                   Multiple-choice: answer = the option text e.g. `"Option A"`
                   Multi-select: comma-separated e.g. `"Option A,Option C"`
                   True/False: `"True"` or `"False"`

                   **Auto-graded** (MULTIPLE_CHOICE, MULTI_SELECT, TRUE_FALSE):
                   Score is computed immediately. Enrollment moves to COMPLETED or FAILED.

                   **Manual review** (SHORT_ANSWER questions present):
                   Status becomes PENDING_REVIEW until a trainer submits via `POST /review/{attemptId}`.
                   """)
    public ResponseEntity<ApiResponse<AssessmentAttemptResponse>> submit(
            @PathVariable Long enrollmentId,
            @Valid @RequestBody AssessmentAnswerRequest req) {
        return ApiResponse.ok("Assessment submitted",
                assessmentService.submitAttempt(enrollmentId, req));
    }

    @GetMapping("/attempts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all assessment attempts for this enrollment (most recent first)")
    public ResponseEntity<ApiResponse<List<AssessmentAttemptResponse>>> getAttempts(
            @PathVariable Long enrollmentId) {
        return ApiResponse.ok(assessmentService.getAttemptsByEnrollment(enrollmentId));
    }

    @PostMapping("/review/{attemptId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "Manually grade a PENDING_REVIEW attempt (SHORT_ANSWER questions)",
               description = "Provide a percentage score (0–100) and optional review comments.")
    public ResponseEntity<ApiResponse<AssessmentAttemptResponse>> review(
            @PathVariable Long   enrollmentId,
            @PathVariable Long   attemptId,
            @RequestParam int    scorePercent,
            @RequestParam(required = false) String comments) {
        return ApiResponse.ok("Attempt reviewed",
                assessmentService.reviewAttempt(attemptId, scorePercent, comments));
    }

    @GetMapping("/pending-reviews")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "List all assessment attempts pending manual review across all programs")
    public ResponseEntity<ApiResponse<List<AssessmentAttemptResponse>>> pendingReviews(
            @PathVariable Long enrollmentId) {
        return ApiResponse.ok(assessmentService.getPendingReviews());
    }
}
