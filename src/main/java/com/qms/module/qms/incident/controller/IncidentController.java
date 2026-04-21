package com.qms.module.qms.incident.controller;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.qms.common.dto.request.WorkflowRequest;
import com.qms.module.qms.incident.dto.request.IncidentRequest;
import com.qms.module.qms.incident.dto.response.IncidentResponse;
import com.qms.module.qms.incident.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/qms/incidents")
@RequiredArgsConstructor
@Tag(name = "Incident", description = "Incident management — safety, quality, environmental events")
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> search(
            @RequestParam(required = false) QmsStatus status,
            @RequestParam(required = false) Priority  priority,
            @RequestParam(required = false) String    severity,
            @RequestParam(required = false) String    incidentType,
            @RequestParam(required = false) Long      assignedTo,
            @RequestParam(required = false) String    department,
            @RequestParam(required = false) String    search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(incidentService.search(status, priority, severity,
                incidentType, assignedTo, department, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    public ResponseEntity<ApiResponse<IncidentResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(incidentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Report a new incident — any authenticated user can report")
    public ResponseEntity<ApiResponse<IncidentResponse>> create(@Valid @RequestBody IncidentRequest req) {
        return ApiResponse.created("Incident reported", incidentService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> update(@PathVariable Long id, @Valid @RequestBody IncidentRequest req) {
        return ApiResponse.ok("Incident updated", incidentService.update(id, req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> transition(@PathVariable Long id, @Valid @RequestBody WorkflowRequest req) {
        return ApiResponse.ok("Status updated", incidentService.transition(id, req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> submit(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Submitted for approval", incidentService.submit(id, comment));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> approve(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Incident approved", incidentService.approve(id, comment));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> reject(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Incident rejected", incidentService.reject(id, comment));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> close(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Incident closed", incidentService.close(id, comment));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> cancel(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Incident cancelled", incidentService.cancel(id, comment));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<IncidentResponse>> reopen(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Incident reopened", incidentService.reopen(id, comment));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        incidentService.delete(id);
        return ApiResponse.noContent("Incident deleted");
    }
}
