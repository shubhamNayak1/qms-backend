package com.qms.module.user.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.user.dto.response.UserPermissionOverrideResponse;
import com.qms.module.user.service.UserPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Bulk variant of /api/v1/users/{userId}/permission-overrides for the
 * Permission matrix (User × Permission). One HTTP call fetches override
 * rows for N users; the matrix UI then renders without N round-trips.
 *
 *   GET /api/v1/permission-overrides?userIds=1,2,3
 *   → { "data": { "1": [...], "2": [...], "3": [] } }
 *
 * The response always carries every requested id; users with no override
 * rows ship as an empty array so the client can tell "nothing to override"
 * apart from "not yet loaded".
 */
@RestController
@RequestMapping("/api/v1/permission-overrides")
@RequiredArgsConstructor
@Tag(name = "User Permission Overrides (bulk)",
     description = "Fetch per-user permission overrides for many users in one call")
@SecurityRequirement(name = "bearerAuth")
public class UserPermissionBulkController {

    private final UserPermissionService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_VIEW','PERM_MANAGE') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Bulk-fetch permission overrides for every userId in the list")
    public ResponseEntity<ApiResponse<Map<Long, List<UserPermissionOverrideResponse>>>> listMany(
            @RequestParam List<Long> userIds) {
        return ApiResponse.ok(service.listForUsers(userIds));
    }
}
