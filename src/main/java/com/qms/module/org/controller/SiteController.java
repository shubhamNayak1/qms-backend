package com.qms.module.org.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.org.dto.request.SiteRequest;
import com.qms.module.org.dto.response.SiteResponse;
import com.qms.module.org.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/org/site")
@RequiredArgsConstructor
@Tag(name = "Org Structure — Site", description = "Read and manage the (single) Site profile")
@SecurityRequirement(name = "bearerAuth")
public class SiteController {

    private final SiteService siteService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the active site profile (singleton)")
    public ResponseEntity<ApiResponse<SiteResponse>> get() {
        return ApiResponse.ok(siteService.getDefault());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update site name / address / Site Head")
    public ResponseEntity<ApiResponse<SiteResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SiteRequest req) {
        return ApiResponse.ok("Site updated", siteService.update(id, req));
    }
}
