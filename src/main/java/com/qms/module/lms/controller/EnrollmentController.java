package com.qms.module.lms.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.lms.dto.request.BulkEnrollmentRequest;
import com.qms.module.lms.dto.request.ContentProgressRequest;
import com.qms.module.lms.dto.request.EnrollmentRequest;
import com.qms.module.lms.dto.request.WaiverRequest;
import com.qms.module.lms.dto.response.EnrollmentResponse;
import com.qms.module.lms.enums.EnrollmentStatus;
import com.qms.module.lms.service.EnrollmentService;
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
@RequestMapping("/api/v1/lms/enrollments")
@RequiredArgsConstructor
@Tag(name = "LMS — Enrollments", description = "Assign training programs to users and track completion")
@SecurityRequirement(name = "bearerAuth")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    // ── Search & Read ────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','MANAGER','AUDITOR')")
    @Operation(summary = "Search enrollments with optional filters",
               description = "Pass overdue=true to list only overdue assignments.")
    public ResponseEntity<ApiResponse<PageResponse<EnrollmentResponse>>> search(
            @RequestParam(required = false) Long             userId,
            @RequestParam(required = false) Long             programId,
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(required = false) String           department,
            @RequestParam(required = false) Boolean          overdue,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(enrollmentService.search(userId, programId, status, department, overdue, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','MANAGER','AUDITOR') " +
                  "or @enrollmentOwnerChecker.check(#id, authentication)")
    @Operation(summary = "Get enrollment details with full content progress list")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(enrollmentService.getById(id));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the current user's own enrollments")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> getMyEnrollments() {
        return ApiResponse.ok(enrollmentService.getMyEnrollments());
    }

    // ── Enroll ───────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','MANAGER')")
    @Operation(summary = "Assign a training program to a single user")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enroll(
            @Valid @RequestBody EnrollmentRequest req) {
        return ApiResponse.created("User enrolled successfully", enrollmentService.enroll(req));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "Assign a training program to multiple users at once",
               description = "Users who are already actively enrolled are silently skipped.")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> bulkEnroll(
            @Valid @RequestBody BulkEnrollmentRequest req) {
        return ApiResponse.created("Bulk enrollment complete", enrollmentService.bulkEnroll(req));
    }

    // ── Progress reporting ───────────────────────────────────

    @PostMapping("/{id}/progress")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Report content view progress for the current user",
               description = """
                   Send this after each content interaction:
                   - Set **viewPercent=100** to mark a video as watched
                   - Set **acknowledged=true** to confirm reading a DOCUMENT item
                   - Provide **sessionTimeSeconds** to accumulate time-on-task metrics

                   The enrollment automatically moves to IN_PROGRESS on first call,
                   and to COMPLETED when all required items are done (if no assessment).
                   """)
    public ResponseEntity<ApiResponse<EnrollmentResponse>> updateProgress(
            @PathVariable Long id,
            @Valid @RequestBody ContentProgressRequest req) {
        return ApiResponse.ok("Progress updated", enrollmentService.updateProgress(id, req));
    }

    // ── Waiver & Cancel ──────────────────────────────────────

    @PostMapping("/{id}/waive")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','MANAGER')")
    @Operation(summary = "Grant a training waiver",
               description = "A waived enrollment counts as compliant for reporting purposes.")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> waive(
            @PathVariable Long id,
            @Valid @RequestBody WaiverRequest req) {
        return ApiResponse.ok("Waiver granted", enrollmentService.grantWaiver(id, req));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "Cancel an enrollment",
               description = "Cancelled enrollments do NOT count as compliant.")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return ApiResponse.ok("Enrollment cancelled", enrollmentService.cancel(id, reason));
    }
}
