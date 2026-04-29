package com.qms.module.lms.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.lms.dto.response.CertificateResponse;
import com.qms.module.lms.enums.CertificateStatus;
import com.qms.module.lms.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lms/certificates")
@RequiredArgsConstructor
@Tag(name = "LMS — Certificates", description = "Training completion certificates — issue, view, revoke")
@SecurityRequirement(name = "bearerAuth")
public class CertificateController {

    private final CertificateService certificateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','AUDITOR')")
    @Operation(summary = "List all certificates (paginated)",
               description = "Managers can list all certificates, optionally filtered by status.")
    public ResponseEntity<ApiResponse<PageResponse<CertificateResponse>>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    CertificateStatus status) {
        return ApiResponse.ok(certificateService.getAll(page, size, status));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','AUDITOR') or #userId == authentication.principal.id")
    @Operation(summary = "Get all certificates for a user (most recent first)",
               description = "Users can view their own certificates. Managers can view anyone's.")
    public ResponseEntity<ApiResponse<PageResponse<CertificateResponse>>> getByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(certificateService.getByUser(userId, page, size));
    }

    @GetMapping("/number/{certificateNumber}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Look up a certificate by its unique certificate number",
               description = "Used for certificate verification by third parties.")
    public ResponseEntity<ApiResponse<CertificateResponse>> getByNumber(
            @PathVariable String certificateNumber) {
        return ApiResponse.ok(certificateService.getByNumber(certificateNumber));
    }

    @GetMapping("/enrollment/{enrollmentId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the certificate issued for a specific enrollment")
    public ResponseEntity<ApiResponse<CertificateResponse>> getByEnrollment(
            @PathVariable Long enrollmentId) {
        return ApiResponse.ok(certificateService.getByEnrollment(enrollmentId));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Revoke a certificate",
               description = "Used when a compliance breach invalidates prior training. Reason is mandatory.")
    public ResponseEntity<ApiResponse<CertificateResponse>> revoke(
            @PathVariable Long id,
            @RequestParam String reason) {
        return ApiResponse.ok("Certificate revoked", certificateService.revoke(id, reason));
    }
}
