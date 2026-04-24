package com.qms.module.qms.complaint.controller;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.qms.common.dto.request.WorkflowRequest;
import com.qms.module.qms.complaint.dto.request.MarketComplaintRequest;
import com.qms.module.qms.complaint.dto.response.MarketComplaintResponse;
import com.qms.module.qms.complaint.service.MarketComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/qms/complaints")
@RequiredArgsConstructor
@Tag(name = "Market Complaint", description = "Market complaint management — customer feedback and complaints")
@SecurityRequirement(name = "bearerAuth")
public class MarketComplaintController {

    private final MarketComplaintService complaintService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PageResponse<MarketComplaintResponse>>> search(
            @RequestParam(required = false) QmsStatus status,
            @RequestParam(required = false) Priority  priority,
            @RequestParam(required = false) String    category,
            @RequestParam(required = false) Long      assignedTo,
            @RequestParam(required = false) Boolean   reportableOnly,
            @RequestParam(required = false) String    search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(complaintService.search(status, priority, category,
                assignedTo, reportableOnly, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','AUDITOR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<MarketComplaintResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(complaintService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','EMPLOYEE')")
    @Operation(summary = "Log a new market complaint received from a customer")
    public ResponseEntity<ApiResponse<MarketComplaintResponse>> create(@Valid @RequestBody MarketComplaintRequest req) {
        return ApiResponse.created("Market Complaint created", complaintService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<MarketComplaintResponse>> update(@PathVariable Long id, @Valid @RequestBody MarketComplaintRequest req) {
        return ApiResponse.ok("Complaint updated", complaintService.update(id, req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    public ResponseEntity<ApiResponse<MarketComplaintResponse>> transition(@PathVariable Long id, @Valid @RequestBody WorkflowRequest req) {
        return ApiResponse.ok("Status updated", complaintService.transition(id, req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','QA_OFFICER')")
    public ResponseEntity<ApiResponse<MarketComplaintResponse>> submit(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Submitted for review", complaintService.submit(id, comment));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<MarketComplaintResponse>> approve(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Complaint approved", complaintService.approve(id, comment));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<MarketComplaintResponse>> reject(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Complaint rejected", complaintService.reject(id, comment));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<MarketComplaintResponse>> close(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Complaint closed", complaintService.close(id, comment));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<MarketComplaintResponse>> cancel(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Complaint cancelled", complaintService.cancel(id, comment));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    public ResponseEntity<ApiResponse<MarketComplaintResponse>> reopen(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return ApiResponse.ok("Complaint reopened", complaintService.reopen(id, comment));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        complaintService.delete(id);
        return ApiResponse.noContent("Complaint deleted");
    }
}
