package com.qms.module.user.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.user.dto.request.CreatePermissionRequest;
import com.qms.module.user.dto.response.PermissionResponse;
import com.qms.module.user.service.PermissionService;
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
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "Permission Management",
     description = "Registry of fine-grained permissions assigned to roles")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PermissionController {

    private final PermissionService permissionService;

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/permissions
    // ─────────────────────────────────────────────────────────
    @GetMapping
    @Operation(summary = "List all permissions (paginated)",
               description = "Filter by module name (USER, CAPA, DMS, LMS, REPORT) and/or search term.")
    public ResponseEntity<ApiResponse<PageResponse<PermissionResponse>>> getAll(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(permissionService.getAll(module, search, page, size));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/permissions/all
    // ─────────────────────────────────────────────────────────
    @GetMapping("/all")
    @Operation(summary = "Get all permissions as a flat list (for role assignment UI)")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllFlat() {
        return ApiResponse.ok(permissionService.getAllFlat());
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/permissions/{id}
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @Operation(summary = "Get permission by ID")
    public ResponseEntity<ApiResponse<PermissionResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(permissionService.getById(id));
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/permissions
    // ─────────────────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Create a new permission",
               description = "Name must be UPPER_SNAKE_CASE, e.g. CAPA_APPROVE, USER_CREATE.")
    public ResponseEntity<ApiResponse<PermissionResponse>> create(
            @Valid @RequestBody CreatePermissionRequest request) {
        return ApiResponse.created("Permission created successfully",
                permissionService.create(request));
    }

    // ─────────────────────────────────────────────────────────
    // PUT /api/v1/permissions/{id}
    // ─────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @Operation(summary = "Update permission metadata")
    public ResponseEntity<ApiResponse<PermissionResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CreatePermissionRequest request) {
        return ApiResponse.ok("Permission updated successfully",
                permissionService.update(id, request));
    }

    // ─────────────────────────────────────────────────────────
    // DELETE /api/v1/permissions/{id}
    // ─────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a permission",
               description = "Fails if the permission is still assigned to any role.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ApiResponse.noContent("Permission deleted successfully");
    }
}
