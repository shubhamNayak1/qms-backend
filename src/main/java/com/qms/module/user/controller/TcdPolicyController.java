package com.qms.module.user.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.user.dto.request.TcdPolicyRequest;
import com.qms.module.user.dto.response.TcdPolicyResponse;
import com.qms.module.user.service.TcdPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin API for managing TCD (Target Closure Days) policy rows.
 *
 * One row per QMS module. The policy drives due-date behaviour across
 * the workflow services; Phase 1 just exposes read + update endpoints
 * so the Admin → Policy tab can edit them.
 *
 * Endpoints:
 *   GET /api/v1/admin/tcd-policy             — list all module rows
 *   GET /api/v1/admin/tcd-policy/{moduleKey} — single module row
 *   PUT /api/v1/admin/tcd-policy/{moduleKey} — update one row
 */
@RestController
@RequestMapping("/api/v1/admin/tcd-policy")
@RequiredArgsConstructor
@Tag(name = "TCD Policy", description = "Manage per-module target closure days")
@SecurityRequirement(name = "bearerAuth")
public class TcdPolicyController {

    private final TcdPolicyService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','QA_MANAGER')")
    @Operation(summary = "List every module's TCD policy row")
    public ResponseEntity<ApiResponse<List<TcdPolicyResponse>>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{moduleKey}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','QA_MANAGER')")
    @Operation(summary = "Get one module's TCD policy row")
    public ResponseEntity<ApiResponse<TcdPolicyResponse>> getOne(@PathVariable String moduleKey) {
        return ApiResponse.ok(service.getByModule(moduleKey.toUpperCase()));
    }

    @PutMapping("/{moduleKey}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @Operation(summary = "Update one module's TCD policy row")
    public ResponseEntity<ApiResponse<TcdPolicyResponse>> update(
            @PathVariable String moduleKey,
            @Valid @RequestBody TcdPolicyRequest req) {
        return ApiResponse.ok("TCD policy updated", service.update(moduleKey.toUpperCase(), req));
    }
}
