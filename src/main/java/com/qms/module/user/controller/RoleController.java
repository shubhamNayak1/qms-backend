package com.qms.module.user.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.user.dto.request.AssignPermissionsRequest;
import com.qms.module.user.dto.request.CreateRoleRequest;
import com.qms.module.user.dto.response.RoleResponse;
import com.qms.module.user.service.RoleService;
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
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "Create and manage roles, assign permissions to roles")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")   // all endpoints in this controller require SUPER_ADMIN
public class RoleController {

    private final RoleService roleService;

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/roles
    // ─────────────────────────────────────────────────────────
    @GetMapping
    @Operation(summary = "List all roles (paginated)",
               description = "Returns each role with its permissions and active user count.")
    public ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(roleService.getAll(search, page, size));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/roles/all
    // ─────────────────────────────────────────────────────────
    @GetMapping("/all")
    @Operation(summary = "Get all roles as a flat list (for dropdowns)")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllFlat() {
        return ApiResponse.ok(roleService.getAllFlat());
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/roles/{id}
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @Operation(summary = "Get role details including all assigned permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(roleService.getById(id));
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/roles
    // ─────────────────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Create a new role",
               description = "Name must be UPPER_SNAKE_CASE. Optionally assign permissions at creation.")
    public ResponseEntity<ApiResponse<RoleResponse>> create(
            @Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.created("Role created successfully",
                roleService.create(request));
    }

    // ─────────────────────────────────────────────────────────
    // PUT /api/v1/roles/{id}
    // ─────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @Operation(summary = "Update role metadata (name, displayName, description)",
               description = "Use PATCH /{id}/permissions to change permission assignments.")
    public ResponseEntity<ApiResponse<RoleResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok("Role updated successfully",
                roleService.update(id, request));
    }

    // ─────────────────────────────────────────────────────────
    // PATCH /api/v1/roles/{id}/permissions
    // ─────────────────────────────────────────────────────────
    @PatchMapping("/{id}/permissions")
    @Operation(summary = "Assign (replace) permissions for a role",
               description = """
                   Replaces the full permission set. Pass an empty array to revoke all permissions.
                   Changes take effect on the user's **next** token refresh.
                   """)
    public ResponseEntity<ApiResponse<RoleResponse>> assignPermissions(
            @PathVariable Long id,
            @Valid @RequestBody AssignPermissionsRequest request) {
        return ApiResponse.ok("Permissions assigned successfully",
                roleService.assignPermissions(id, request));
    }

    // ─────────────────────────────────────────────────────────
    // DELETE /api/v1/roles/{id}
    // ─────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a role",
               description = "Fails if any active users are still assigned to this role. System roles cannot be deleted.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.noContent("Role deleted successfully");
    }
}
