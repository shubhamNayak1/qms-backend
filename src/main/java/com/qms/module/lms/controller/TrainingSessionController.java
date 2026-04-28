package com.qms.module.lms.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.lms.dto.request.AttendanceRequest;
import com.qms.module.lms.dto.request.TrainingSessionRequest;
import com.qms.module.lms.dto.response.TrainingSessionResponse;
import com.qms.module.lms.service.TrainingSessionService;
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
@RequestMapping("/api/v1/lms")
@RequiredArgsConstructor
@Tag(name = "LMS — Training Sessions", description = "Manage sessions, attendance, and scheduling for training programs")
@SecurityRequirement(name = "bearerAuth")
public class TrainingSessionController {

    private final TrainingSessionService sessionService;

    // ── Sessions under a program ─────────────────────────────

    @GetMapping("/programs/{programId}/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all sessions for a training program")
    public ResponseEntity<ApiResponse<List<TrainingSessionResponse>>> getByProgram(
            @PathVariable Long programId) {
        return ApiResponse.ok(sessionService.getByProgram(programId));
    }

    @PostMapping("/programs/{programId}/sessions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "Create a training session for a program",
               description = "Program must be in APPROVED, PLANNED, or ACTIVE status. " +
                             "Trainer/coordinator defaults to the program's values if not overridden.")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> create(
            @PathVariable Long programId,
            @Valid @RequestBody TrainingSessionRequest req) {
        return ApiResponse.created("Session created", sessionService.create(programId, req));
    }

    // ── Individual session operations ─────────────────────────

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a session with full attendance list")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> getById(
            @PathVariable Long sessionId) {
        return ApiResponse.ok(sessionService.getById(sessionId));
    }

    @PutMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "Update session details (date, venue, trainer, etc.)")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> update(
            @PathVariable Long sessionId,
            @Valid @RequestBody TrainingSessionRequest req) {
        return ApiResponse.ok("Session updated", sessionService.update(sessionId, req));
    }

    @PostMapping("/sessions/{sessionId}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "Cancel a session with an optional reason")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> cancel(
            @PathVariable Long sessionId,
            @RequestParam(required = false) String reason) {
        return ApiResponse.ok("Session cancelled", sessionService.cancel(sessionId, reason));
    }

    @PostMapping("/sessions/{sessionId}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "Mark a session as COMPLETED")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> complete(
            @PathVariable Long sessionId) {
        return ApiResponse.ok("Session completed", sessionService.complete(sessionId));
    }

    // ── Attendance ───────────────────────────────────────────

    @PostMapping("/sessions/{sessionId}/attendance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','COORDINATOR','TRAINER')")
    @Operation(summary = "Mark attendance for trainees in a session",
               description = """
                   Attendance can be marked within ±2 days of the session date.

                   For each present trainee the enrollment is automatically
                   moved to IN_PROGRESS.

                   Send all attendees in one call — repeated calls upsert existing records.
                   """)
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> markAttendance(
            @PathVariable Long sessionId,
            @Valid @RequestBody AttendanceRequest req) {
        return ApiResponse.ok("Attendance marked", sessionService.markAttendance(sessionId, req));
    }
}
