package com.qms.module.qmsaudit.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.qmsaudit.dto.request.CreateAuditScheduleRequest;
import com.qms.module.qmsaudit.dto.request.UpdateAuditScheduleRequest;
import com.qms.module.qmsaudit.dto.response.AuditScheduleResponse;
import com.qms.module.qmsaudit.enums.AuditScheduleStatus;
import com.qms.module.qmsaudit.enums.AuditType;
import com.qms.module.qmsaudit.service.AuditScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/qms-audits")
@RequiredArgsConstructor
@Tag(name = "QMS Audits", description = "Schedule and manage internal, external, supplier and regulatory audits")
@SecurityRequirement(name = "bearerAuth")
public class AuditScheduleController {

    private final AuditScheduleService service;

    // ── List / Search ─────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR','QA_ENGINEER')")
    @Operation(summary = "Search / list audits",
               description = "Filter by type, status, date range, or free-text search across title, number and auditor name.")
    public ResponseEntity<ApiResponse<PageResponse<AuditScheduleResponse>>> search(
            @RequestParam(required = false) AuditType type,
            @RequestParam(required = false) AuditScheduleStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(type, status, from, to, search, page, size));
    }

    // ── Get by ID ─────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR','QA_ENGINEER')")
    @Operation(summary = "Get audit by ID")
    public ResponseEntity<ApiResponse<AuditScheduleResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(service.getById(id));
    }

    // ── Create ────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Schedule a new audit",
               description = "Creates a new audit in PLANNED status. auditType: INTERNAL | EXTERNAL | SUPPLIER | REGULATORY")
    public ResponseEntity<ApiResponse<AuditScheduleResponse>> create(
            @Valid @RequestBody CreateAuditScheduleRequest req) {
        return ApiResponse.created("Audit scheduled successfully", service.create(req));
    }

    // ── Update ────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Update audit details",
               description = "Partial update — only provided fields are changed.")
    public ResponseEntity<ApiResponse<AuditScheduleResponse>> update(
            @PathVariable Long id,
            @RequestBody UpdateAuditScheduleRequest req) {
        return ApiResponse.ok("Audit updated", service.update(id, req));
    }

    // ── Workflow ──────────────────────────────────────────────

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(summary = "Start audit — PLANNED → IN_PROGRESS")
    public ResponseEntity<ApiResponse<AuditScheduleResponse>> start(@PathVariable Long id) {
        return ApiResponse.ok("Audit started", service.start(id));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(summary = "Complete audit — IN_PROGRESS → COMPLETED",
               description = "Optionally pass findings and observations in request params.")
    public ResponseEntity<ApiResponse<AuditScheduleResponse>> complete(
            @PathVariable Long id,
            @RequestParam(required = false) String findings,
            @RequestParam(required = false) String observations) {
        return ApiResponse.ok("Audit completed", service.complete(id, findings, observations));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Cancel audit")
    public ResponseEntity<ApiResponse<AuditScheduleResponse>> cancel(@PathVariable Long id) {
        return ApiResponse.ok("Audit cancelled", service.cancel(id));
    }

    // ── Delete ────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Soft-delete an audit")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("Audit deleted", null);
    }
}
