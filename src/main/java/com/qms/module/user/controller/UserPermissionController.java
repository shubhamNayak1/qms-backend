package com.qms.module.user.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.user.dto.request.UserPermissionOverrideRequest;
import com.qms.module.user.dto.response.UserPermissionOverrideResponse;
import com.qms.module.user.service.UserPermissionService;
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
 * UserPermissionController — per-user grant/revoke overrides on top of
 * role-based permissions. Exposed to the Permission matrix UI.
 *
 * Endpoints:
 *   GET    /api/v1/users/{userId}/permission-overrides
 *   PUT    /api/v1/users/{userId}/permission-overrides     (upsert)
 *   DELETE /api/v1/users/{userId}/permission-overrides/{permissionId}
 *
 * All writes require PERM_MANAGE; reads require PERM_VIEW or MANAGE.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/permission-overrides")
@RequiredArgsConstructor
@Tag(name = "User Permission Overrides",
     description = "Grant / revoke permissions for individual users on top of role defaults")
@SecurityRequirement(name = "bearerAuth")
public class UserPermissionController {

    private final UserPermissionService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_VIEW','PERM_MANAGE')")
    @Operation(summary = "List the user's permission overrides")
    public ResponseEntity<ApiResponse<List<UserPermissionOverrideResponse>>> list(
            @PathVariable Long userId) {
        return ApiResponse.ok(service.listForUser(userId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_MANAGE')")
    @Operation(summary = "Grant or revoke a permission for the user (idempotent upsert)")
    public ResponseEntity<ApiResponse<UserPermissionOverrideResponse>> upsert(
            @PathVariable Long userId,
            @Valid @RequestBody UserPermissionOverrideRequest req) {
        return ApiResponse.ok("Override saved", service.upsert(userId, req));
    }

    @DeleteMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('PERM_MANAGE')")
    @Operation(summary = "Remove the override row, letting the user's effective perms fall back to their role")
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable Long userId, @PathVariable Long permissionId) {
        service.remove(userId, permissionId);
        return ApiResponse.ok("Override removed", null);
    }
}
