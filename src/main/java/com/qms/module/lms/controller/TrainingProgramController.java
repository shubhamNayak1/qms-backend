package com.qms.module.lms.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.lms.dto.request.ProgramRequest;
import com.qms.module.lms.dto.response.ProgramResponse;
import com.qms.module.lms.enums.ContentType;
import com.qms.module.lms.enums.ProgramStatus;
import com.qms.module.lms.service.TrainingProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lms/programs")
@RequiredArgsConstructor
@Tag(name = "LMS — Training Programs", description = "CREATE → REVIEW → PLAN → ALLOCATE → TRAIN → COMPLIANCE → RESULT")
@SecurityRequirement(name = "bearerAuth")
public class TrainingProgramController {

    private final TrainingProgramService programService;

    // ── Search & Read ────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search training programs with optional filters")
    public ResponseEntity<ApiResponse<PageResponse<ProgramResponse>>> search(
            @RequestParam(required = false) ProgramStatus status,
            @RequestParam(required = false) String        category,
            @RequestParam(required = false) String        department,
            @RequestParam(required = false) Boolean       mandatory,
            @RequestParam(required = false) String        search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(programService.search(status, category, department, mandatory,
                search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get training program by ID (includes sessions, contents, document links)")
    public ResponseEntity<ApiResponse<ProgramResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(programService.getById(id));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get training program by code e.g. GMP-001")
    public ResponseEntity<ApiResponse<ProgramResponse>> getByCode(@PathVariable String code) {
        return ApiResponse.ok(programService.getByCode(code));
    }

    // ── STEP 1: Create & Update ──────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "STEP 1 — Create a training program (starts in DRAFT)",
               description = """
                   **Required fields:** code, title, trainingType

                   **trainingType:**
                   - `SCHEDULED` — Instructor-led with fixed date/venue
                   - `SELF` — Self-paced; trainee reads/watches materials independently
                   - `INDUCTION` — New-employee onboarding; HR + QA Head approval chain

                   **trainingSubType:** TEMPORARY | REGULAR | REFRESHER | OJT

                   **examEnabled:** Set to true to attach an exam; false = compliance alone decides pass/fail.
                   """)
    public ResponseEntity<ApiResponse<ProgramResponse>> create(@Valid @RequestBody ProgramRequest req) {
        return ApiResponse.created("Training program created", programService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Update training program metadata (any non-ARCHIVED status)")
    public ResponseEntity<ApiResponse<ProgramResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ProgramRequest req) {
        return ApiResponse.ok("Program updated", programService.update(id, req));
    }

    // ── STEP 2: Review & Approval ────────────────────────────

    @PostMapping("/{id}/raise-review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "STEP 2a — Raise for QA review (DRAFT/REJECTED → UNDER_REVIEW)")
    public ResponseEntity<ApiResponse<ProgramResponse>> raiseForReview(@PathVariable Long id) {
        return ApiResponse.ok("Raised for review", programService.raiseForReview(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "STEP 2b — QA approves the program (UNDER_REVIEW → APPROVED)")
    public ResponseEntity<ApiResponse<ProgramResponse>> approve(@PathVariable Long id) {
        return ApiResponse.ok("Program approved", programService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "STEP 2c — QA rejects the program (UNDER_REVIEW → REJECTED)",
               description = "Provide a rejection reason. The program returns to DRAFT when the creator raises it again.")
    public ResponseEntity<ApiResponse<ProgramResponse>> reject(
            @PathVariable Long id,
            @RequestParam String reason) {
        return ApiResponse.ok("Program rejected", programService.reject(id, reason));
    }

    // ── STEP 3: Plan ─────────────────────────────────────────

    @PostMapping("/{id}/plan")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "STEP 3 — Mark as PLANNED (APPROVED → PLANNED)",
               description = "Requires at least one training session to be created first. " +
                             "Use POST /programs/{id}/sessions to add sessions.")
    public ResponseEntity<ApiResponse<ProgramResponse>> markPlanned(@PathVariable Long id) {
        return ApiResponse.ok("Program planned", programService.markPlanned(id));
    }

    // ── STEP 4: Allocate & Activate ──────────────────────────

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','MANAGER')")
    @Operation(summary = "STEP 4 — Approve allocation and activate (PLANNED → ACTIVE)",
               description = "After enrollments are created, the manager approves the allocation. " +
                             "This makes the program live — trainees can now see and start it.")
    public ResponseEntity<ApiResponse<ProgramResponse>> activate(@PathVariable Long id) {
        return ApiResponse.ok("Program activated", programService.activate(id));
    }

    // ── STEP 9: Complete ─────────────────────────────────────

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "STEP 9 — Mark program as COMPLETED (ACTIVE → COMPLETED)",
               description = "Called when all sessions have finished. " +
                             "Enrollments continue their own lifecycle independently.")
    public ResponseEntity<ApiResponse<ProgramResponse>> complete(@PathVariable Long id) {
        return ApiResponse.ok("Program completed", programService.complete(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Archive a program — no new enrollments allowed")
    public ResponseEntity<ApiResponse<ProgramResponse>> archive(@PathVariable Long id) {
        return ApiResponse.ok("Program archived", programService.archive(id));
    }

    // ── Content management (STEP 1 sub-actions) ──────────────

    @PostMapping("/{id}/contents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Add a content/material item to the program",
               description = """
                   Provide fields relevant to the contentType:
                   - **DOCUMENT**: dmsDocumentId, dmsDocNumber, dmsDocVersion
                   - **VIDEO / EXTERNAL_LINK / SCORM**: contentUrl
                   - **TEXT**: inlineContent
                   - **SLIDE_DECK**: contentUrl (PDF/PPT hosted URL)
                   """)
    public ResponseEntity<ApiResponse<ProgramResponse>> addContent(
            @PathVariable Long id,
            @RequestParam String      title,
            @RequestParam ContentType contentType,
            @RequestParam(required = false) String  contentUrl,
            @RequestParam(required = false) Long    dmsDocumentId,
            @RequestParam(required = false) String  dmsDocNumber,
            @RequestParam(required = false) String  dmsDocVersion,
            @RequestParam(required = false) String  inlineContent,
            @RequestParam(required = false) Integer durationMinutes,
            @RequestParam(required = false) Boolean required) {
        return ApiResponse.created("Content added",
                programService.addContent(id, title, contentType, contentUrl,
                        dmsDocumentId, dmsDocNumber, dmsDocVersion, inlineContent,
                        durationMinutes, required));
    }

    @DeleteMapping("/{id}/contents/{contentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Remove a content item from the program")
    public ResponseEntity<ApiResponse<ProgramResponse>> removeContent(
            @PathVariable Long id, @PathVariable Long contentId) {
        return ApiResponse.ok("Content removed", programService.removeContent(id, contentId));
    }

    // ── DMS document linking ─────────────────────────────────

    @PostMapping("/{id}/document-links")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Link a DMS document as reference material for this program")
    public ResponseEntity<ApiResponse<ProgramResponse>> linkDocument(
            @PathVariable Long id,
            @RequestParam Long    dmsDocumentId,
            @RequestParam String  dmsDocNumber,
            @RequestParam String  dmsDocVersion,
            @RequestParam(required = false) String  dmsDocTitle,
            @RequestParam(required = false, defaultValue = "true") Boolean triggerReview) {
        return ApiResponse.created("Document linked",
                programService.linkDocument(id, dmsDocumentId, dmsDocNumber,
                        dmsDocVersion, dmsDocTitle, triggerReview));
    }

    @DeleteMapping("/{id}/document-links/{linkId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Remove a DMS document link from this program")
    public ResponseEntity<ApiResponse<ProgramResponse>> unlinkDocument(
            @PathVariable Long id, @PathVariable Long linkId) {
        return ApiResponse.ok("Document link removed", programService.unlinkDocument(id, linkId));
    }

    // ── Delete ───────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Soft-delete a program (blocked if in-progress enrollments exist)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        programService.delete(id);
        return ApiResponse.noContent("Program deleted");
    }
}
