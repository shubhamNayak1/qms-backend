package com.qms.module.qms.changecontrol.controller;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.qms.changecontrol.dto.request.ChangeControlRequest;
import com.qms.module.qms.changecontrol.dto.response.ChangeControlResponse;
import com.qms.module.qms.changecontrol.service.ChangeControlService;
import com.qms.module.qms.common.dto.request.WorkflowRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/qms/change-controls")
@RequiredArgsConstructor
@Tag(name = "Change Control", description = "Change control management — process, equipment, document changes")
@SecurityRequirement(name = "bearerAuth")
public class ChangeControlController {

    private final ChangeControlService changeControlService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PageResponse<ChangeControlResponse>>> search(
            @RequestParam(required = false) QmsStatus status,
            @RequestParam(required = false) Priority  priority,
            @RequestParam(required = false) String    changeType,
            @RequestParam(required = false) String    riskLevel,
            @RequestParam(required = false) Long      assignedTo,
            @RequestParam(required = false) String    department,
            @RequestParam(required = false) String    search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(changeControlService.search(status, priority, changeType,
                riskLevel, assignedTo, department, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<ChangeControlResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(changeControlService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','EMPLOYEE')")
    @Operation(summary = "Raise a new Change Control request")
    public ResponseEntity<ApiResponse<ChangeControlResponse>> create(@Valid @RequestBody ChangeControlRequest req) {
        return ApiResponse.created("Change Control created", changeControlService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<ChangeControlResponse>> update(@PathVariable Long id, @Valid @RequestBody ChangeControlRequest req) {
        return ApiResponse.ok("Change Control updated", changeControlService.update(id, req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    public ResponseEntity<ApiResponse<ChangeControlResponse>> transition(@PathVariable Long id, @Valid @RequestBody WorkflowRequest req) {
        return ApiResponse.ok("Status updated", changeControlService.transition(id, req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    public ResponseEntity<ApiResponse<ChangeControlResponse>> submit(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Submitted for approval", changeControlService.submit(id, comment));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<ChangeControlResponse>> approve(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Change Control approved", changeControlService.approve(id, comment));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<ChangeControlResponse>> reject(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Change Control rejected", changeControlService.reject(id, comment));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<ChangeControlResponse>> close(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Change Control closed", changeControlService.close(id, comment));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<ChangeControlResponse>> cancel(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Change Control cancelled", changeControlService.cancel(id, comment));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<ChangeControlResponse>> reopen(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Change Control reopened", changeControlService.reopen(id, comment));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        changeControlService.delete(id);
        return ApiResponse.noContent("Change Control deleted");
    }
}
