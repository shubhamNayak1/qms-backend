package com.qms.module.qms.deviation.controller;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.qms.common.dto.request.WorkflowRequest;
import com.qms.module.qms.deviation.dto.request.DeviationRequest;
import com.qms.module.qms.deviation.dto.response.DeviationResponse;
import com.qms.module.qms.deviation.service.DeviationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/qms/deviations")
@RequiredArgsConstructor
@Tag(name = "Deviation", description = "Deviation management — planned and unplanned")
@SecurityRequirement(name = "bearerAuth")
public class DeviationController {

    private final DeviationService deviationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    public ResponseEntity<ApiResponse<PageResponse<DeviationResponse>>> search(
            @RequestParam(required = false) QmsStatus status,
            @RequestParam(required = false) Priority  priority,
            @RequestParam(required = false) Long      assignedTo,
            @RequestParam(required = false) String    department,
            @RequestParam(required = false) String    deviationType,
            @RequestParam(required = false) String    search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(deviationService.search(status, priority, assignedTo, department, deviationType, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR')")
    public ResponseEntity<ApiResponse<DeviationResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(deviationService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    @Operation(summary = "Report a new deviation")
    public ResponseEntity<ApiResponse<DeviationResponse>> create(@Valid @RequestBody DeviationRequest req) {
        return ApiResponse.created("Deviation created", deviationService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    public ResponseEntity<ApiResponse<DeviationResponse>> update(@PathVariable Long id, @Valid @RequestBody DeviationRequest req) {
        return ApiResponse.ok("Deviation updated", deviationService.update(id, req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    public ResponseEntity<ApiResponse<DeviationResponse>> transition(@PathVariable Long id, @Valid @RequestBody WorkflowRequest req) {
        return ApiResponse.ok("Status updated", deviationService.transition(id, req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    public ResponseEntity<ApiResponse<DeviationResponse>> submit(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Submitted for approval", deviationService.submit(id, comment));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<DeviationResponse>> approve(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Deviation approved", deviationService.approve(id, comment));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<DeviationResponse>> reject(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Deviation rejected", deviationService.reject(id, comment));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<DeviationResponse>> close(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Deviation closed", deviationService.close(id, comment));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<DeviationResponse>> cancel(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Deviation cancelled", deviationService.cancel(id, comment));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<DeviationResponse>> reopen(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Deviation reopened", deviationService.reopen(id, comment));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        deviationService.delete(id);
        return ApiResponse.noContent("Deviation deleted");
    }
}
