package com.qms.module.license.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.license.dto.request.AssignLicenseRequest;
import com.qms.module.license.dto.request.GenerateLicensesRequest;
import com.qms.module.license.dto.response.LicenseResponse;
import com.qms.module.license.dto.response.LicenseStatsResponse;
import com.qms.module.license.enums.LicenseStatus;
import com.qms.module.license.service.LicenseService;
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
@RequestMapping("/api/v1/licenses")
@RequiredArgsConstructor
@Tag(name = "Licenses", description = "Per-seat QMS licenses (login gate)")
@SecurityRequirement(name = "bearerAuth")
public class LicenseController {

    private final LicenseService licenseService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List licenses, optionally filtered by status")
    public ResponseEntity<ApiResponse<PageResponse<LicenseResponse>>> list(
            @RequestParam(required = false) LicenseStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(licenseService.list(status, page, size));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Counts of licenses by status (dashboard tile)")
    public ResponseEntity<ApiResponse<LicenseStatsResponse>> stats() {
        return ApiResponse.ok(licenseService.stats());
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Generate N new AVAILABLE licenses",
               description = "Mints fresh license codes into the AVAILABLE pool. Cap of 500 per call.")
    public ResponseEntity<ApiResponse<List<LicenseResponse>>> generate(
            @Valid @RequestBody GenerateLicensesRequest req) {
        return ApiResponse.created("Licenses generated", licenseService.generate(req));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Assign an AVAILABLE license to a user",
               description = "After this call the user can log in.")
    public ResponseEntity<ApiResponse<LicenseResponse>> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignLicenseRequest req) {
        return ApiResponse.ok("License assigned", licenseService.assign(id, req.getUserId()));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Revoke an ASSIGNED license — user can no longer log in")
    public ResponseEntity<ApiResponse<LicenseResponse>> revoke(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return ApiResponse.ok("License revoked", licenseService.revoke(id, reason));
    }
}
