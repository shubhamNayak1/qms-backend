package com.qms.module.qms.capa.controller;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.qms.capa.dto.request.CapaRequest;
import com.qms.module.qms.capa.dto.request.EffectivenessRequest;
import com.qms.module.qms.capa.dto.response.CapaResponse;
import com.qms.module.qms.capa.service.CapaService;
import com.qms.module.qms.common.dto.request.WorkflowRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/qms/capa")
@RequiredArgsConstructor
@Tag(name = "CAPA", description = "Corrective And Preventive Actions — full lifecycle management")
@SecurityRequirement(name = "bearerAuth")
public class CapaController {

    private final CapaService capaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR','EMPLOYEE')")
    @Operation(summary = "Search / list CAPAs with optional filters")
    public ResponseEntity<ApiResponse<PageResponse<CapaResponse>>> search(
            @RequestParam(required = false) QmsStatus status,
            @RequestParam(required = false) Priority  priority,
            @RequestParam(required = false) Long      assignedTo,
            @RequestParam(required = false) String    department,
            @RequestParam(required = false) String    source,
            @Parameter(description = "Full-text search on title, record number, description")
            @RequestParam(required = false) String    search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(capaService.search(status, priority, assignedTo, department, source, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR','EMPLOYEE')")
    @Operation(summary = "Get a CAPA by database ID")
    public ResponseEntity<ApiResponse<CapaResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(capaService.getById(id));
    }

    @GetMapping("/number/{recordNumber}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR','EMPLOYEE')")
    @Operation(summary = "Get a CAPA by record number e.g. CAPA-202404-0001")
    public ResponseEntity<ApiResponse<CapaResponse>> getByRecordNumber(
            @PathVariable String recordNumber) {
        return ApiResponse.ok(capaService.getByRecordNumber(recordNumber));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','EMPLOYEE')")
    @Operation(summary = "Open a new CAPA record")
    public ResponseEntity<ApiResponse<CapaResponse>> create(
            @Valid @RequestBody CapaRequest request) {
        return ApiResponse.created("CAPA created successfully", capaService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','EMPLOYEE')")
    @Operation(summary = "Update CAPA fields — null fields are ignored")
    public ResponseEntity<ApiResponse<CapaResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CapaRequest request) {
        return ApiResponse.ok("CAPA updated", capaService.update(id, request));
    }

    // ── Workflow endpoints ────────────────────────────────────

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    @Operation(summary = "Generic status transition — specify any allowed targetStatus (PENDING_SITE_HEAD, PENDING_ATTACHMENTS, CANCELLED, etc.)")
    public ResponseEntity<ApiResponse<CapaResponse>> transition(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowRequest request) {
        return ApiResponse.ok("Status updated", capaService.transition(id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    @Operation(summary = "Submit CAPA — DRAFT → PENDING_HOD (Head of Department review)")
    public ResponseEntity<ApiResponse<CapaResponse>> submit(
            @PathVariable Long id,
            @RequestParam(required = false) String comment) {
        return ApiResponse.ok("CAPA submitted for approval", capaService.submit(id, comment));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Approve — advances to canonical next step per workflow (e.g. PENDING_HOD→PENDING_QA_REVIEW, PENDING_HEAD_QA→CLOSED)")
    public ResponseEntity<ApiResponse<CapaResponse>> approve(
            @PathVariable Long id,
            @RequestParam(required = false) String comment) {
        return ApiResponse.ok("CAPA approved", capaService.approve(id, comment));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Reject — moves to REJECTED from any pending state, returns to DRAFT for rework")
    public ResponseEntity<ApiResponse<CapaResponse>> reject(
            @PathVariable Long id,
            @RequestParam(required = false) String comment) {
        return ApiResponse.ok("CAPA rejected", capaService.reject(id, comment));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Close — PENDING_HEAD_QA → CLOSED (final closure by Head of QA)")
    public ResponseEntity<ApiResponse<CapaResponse>> close(
            @PathVariable Long id,
            @RequestParam(required = false) String comment) {
        return ApiResponse.ok("CAPA closed", capaService.close(id, comment));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Cancel a CAPA — any non-terminal status")
    public ResponseEntity<ApiResponse<CapaResponse>> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String comment) {
        return ApiResponse.ok("CAPA cancelled", capaService.cancel(id, comment));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Reopen a closed CAPA — CLOSED → DRAFT (reset for rework)")
    public ResponseEntity<ApiResponse<CapaResponse>> reopen(
            @PathVariable Long id,
            @RequestParam(required = false) String comment) {
        return ApiResponse.ok("CAPA reopened", capaService.reopen(id, comment));
    }

    @PostMapping("/{id}/effectiveness")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Record effectiveness check result for a CLOSED CAPA")
    public ResponseEntity<ApiResponse<CapaResponse>> recordEffectiveness(
            @PathVariable Long id,
            @Valid @RequestBody EffectivenessRequest request) {
        return ApiResponse.ok("Effectiveness recorded", capaService.recordEffectiveness(id, request));
    }

    @GetMapping("/effectiveness/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(summary = "List CAPAs with effectiveness checks due in the next 30 days")
    public ResponseEntity<ApiResponse<List<CapaResponse>>> pendingEffectivenessChecks() {
        return ApiResponse.ok(capaService.getPendingEffectivenessChecks());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Soft-delete a CAPA (data retained for audit)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        capaService.delete(id);
        return ApiResponse.noContent("CAPA deleted");
    }
}
