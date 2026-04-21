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
@Tag(name = "LMS — Training Programs", description = "Create, manage, and publish training programs")
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
        return ApiResponse.ok(programService.search(status, category, department, mandatory, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get training program by ID (includes content outline and document links)")
    public ResponseEntity<ApiResponse<ProgramResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(programService.getById(id));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get training program by code e.g. GMP-001")
    public ResponseEntity<ApiResponse<ProgramResponse>> getByCode(@PathVariable String code) {
        return ApiResponse.ok(programService.getByCode(code));
    }

    // ── Create & Update ──────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Create a new training program (starts in DRAFT)")
    public ResponseEntity<ApiResponse<ProgramResponse>> create(@Valid @RequestBody ProgramRequest req) {
        return ApiResponse.created("Training program created", programService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Update training program metadata")
    public ResponseEntity<ApiResponse<ProgramResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ProgramRequest req) {
        return ApiResponse.ok("Program updated", programService.update(id, req));
    }

    // ── Status transitions ───────────────────────────────────

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Publish DRAFT → ACTIVE (requires at least one content item)")
    public ResponseEntity<ApiResponse<ProgramResponse>> publish(@PathVariable Long id) {
        return ApiResponse.ok("Program published", programService.publish(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Archive an ACTIVE or DRAFT program — no new enrollments allowed")
    public ResponseEntity<ApiResponse<ProgramResponse>> archive(@PathVariable Long id) {
        return ApiResponse.ok("Program archived", programService.archive(id));
    }

    // ── Content management ───────────────────────────────────

    @PostMapping("/{id}/contents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER')")
    @Operation(summary = "Add a content item to the program",
               description = """
                   Provide the fields relevant to the contentType:
                   - **DOCUMENT**: dmsDocumentId, dmsDocNumber, dmsDocVersion
                   - **VIDEO / EXTERNAL_LINK / SCORM**: contentUrl
                   - **TEXT**: inlineContent
                   - **QUIZ**: use the Assessment API to attach a quiz to the program
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
    @Operation(summary = "Link a DMS document as reference material for this program",
               description = "Use triggerReview=true to flag this program for review when the DMS document is updated.")
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
