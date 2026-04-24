package com.qms.module.user.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.user.dto.request.PasswordPolicyRequest;
import com.qms.module.user.dto.response.PasswordPolicyResponse;
import com.qms.module.user.service.PasswordPolicyService;
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
 * Admin API for managing password policies.
 *
 * Endpoint summary:
 *   GET  /api/v1/admin/password-policy/active  — active policy (used by frontend for live validation)
 *   GET  /api/v1/admin/password-policy         — all policies (history)
 *   GET  /api/v1/admin/password-policy/{id}    — single policy by ID
 *   POST /api/v1/admin/password-policy         — create new policy
 *   PUT  /api/v1/admin/password-policy/{id}    — update existing policy
 *   DELETE /api/v1/admin/password-policy/{id}  — soft-delete a policy
 */
@RestController
@RequestMapping("/api/v1/admin/password-policy")
@RequiredArgsConstructor
@Tag(name = "Password Policy", description = "Manage system-wide password complexity and expiry rules")
@SecurityRequirement(name = "bearerAuth")
public class PasswordPolicyController {

    private final PasswordPolicyService policyService;

    // ─── GET active ──────────────────────────────────────────
    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Get currently active password policy",
        description = """
            Returns the policy whose effectiveDate is on or before today.
            This endpoint is open to all authenticated users so the frontend
            can show live password-strength hints on the change-password page.
            """
    )
    public ResponseEntity<ApiResponse<PasswordPolicyResponse>> getActive() {
        return ApiResponse.ok(policyService.getActivePolicy());
    }

    // ─── GET all ─────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(
        summary = "List all password policies",
        description = "Returns all non-deleted policies ordered by effectiveDate descending. " +
                      "The first entry with isActive=true is the current policy."
    )
    public ResponseEntity<ApiResponse<List<PasswordPolicyResponse>>> getAll() {
        return ApiResponse.ok(policyService.getAllPolicies());
    }

    // ─── GET by ID ───────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Get a password policy by ID")
    public ResponseEntity<ApiResponse<PasswordPolicyResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(policyService.getById(id));
    }

    // ─── POST create ─────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Create a new password policy",
        description = """
            Creates a new password policy that will become active on its effectiveDate.
            Only one policy per effectiveDate is allowed.
            Policies with a future effectiveDate are pre-staged but not yet enforced.
            """
    )
    public ResponseEntity<ApiResponse<PasswordPolicyResponse>> create(
            @Valid @RequestBody PasswordPolicyRequest req) {
        return ApiResponse.created("Password policy created", policyService.create(req));
    }

    // ─── PUT update ──────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Update an existing password policy",
        description = "Updates any field, including effectiveDate. " +
                      "Changing the effectiveDate of an already-active policy takes effect immediately."
    )
    public ResponseEntity<ApiResponse<PasswordPolicyResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody PasswordPolicyRequest req) {
        return ApiResponse.ok("Password policy updated", policyService.update(id, req));
    }

    // ─── DELETE ──────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Soft-delete a password policy",
        description = "Marks the policy as deleted. It will no longer appear in listings " +
                      "or be enforced. The system falls back to the next most recent policy."
    )
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        policyService.delete(id);
        return ApiResponse.noContent("Password policy deleted");
    }
}
