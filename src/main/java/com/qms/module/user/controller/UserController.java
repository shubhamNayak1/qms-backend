package com.qms.module.user.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.user.dto.request.*;
import com.qms.module.user.dto.response.UserResponse;
import com.qms.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "CRUD operations for users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/users
    // ─────────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'QA_MANAGER')")
    @Operation(summary = "List all users",
               description = "Paginated, filterable by search term, department, and active status.")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAll(
            @Parameter(description = "Search by name, username, email, or employee ID")
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0")  @Min(0)         int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return ApiResponse.ok(userService.getAll(search, department, isActive, page, size));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/users/{id}
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER') or #id == authentication.principal.id")
    @Operation(summary = "Get a user by ID",
               description = "Admins can view any user. Regular users can only view themselves.")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/users
    // ─────────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create a new user",
               description = "Only SUPER_ADMIN can create users. Password is BCrypt-hashed.")
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.created("User created successfully",
                userService.create(request));
    }

    // ─────────────────────────────────────────────────────────
    // PUT /api/v1/users/{id}
    // ─────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER') or #id == authentication.principal.id")
    @Operation(summary = "Update user profile",
               description = "Null fields are ignored. Use PATCH /users/{id}/roles to change roles.")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok("User updated successfully",
                userService.update(id, request));
    }

    // ─────────────────────────────────────────────────────────
    // PATCH /api/v1/users/{id}/roles
    // ─────────────────────────────────────────────────────────
    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Assign (replace) roles for a user",
               description = """
                   Replaces the user's entire role set atomically.
                   To add a role, include both the existing and new role IDs.
                   """)
    public ResponseEntity<ApiResponse<UserResponse>> assignRoles(
            @PathVariable Long id,
            @Valid @RequestBody AssignRolesRequest request) {
        return ApiResponse.ok("Roles assigned successfully",
                userService.assignRoles(id, request));
    }

    // ─────────────────────────────────────────────────────────
    // PATCH /api/v1/users/{id}/change-password
    // ─────────────────────────────────────────────────────────
    @PatchMapping("/{id}/change-password")
    @PreAuthorize("#id == authentication.principal.id or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Change own password",
               description = "Requires current password verification. SUPER_ADMIN can use admin-reset instead.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request);
        return ApiResponse.noContent("Password changed successfully");
    }

    // ─────────────────────────────────────────────────────────
    // PATCH /api/v1/users/{id}/activate
    // ─────────────────────────────────────────────────────────
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activate a deactivated user account")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        userService.activateUser(id);
        return ApiResponse.noContent("User activated");
    }

    // ─────────────────────────────────────────────────────────
    // PATCH /api/v1/users/{id}/deactivate
    // ─────────────────────────────────────────────────────────
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Deactivate a user account (preserves data)")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ApiResponse.noContent("User deactivated");
    }

    // ─────────────────────────────────────────────────────────
    // DELETE /api/v1/users/{id}
    // ─────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Soft-delete a user",
               description = "Sets is_deleted=true. Data is retained for audit purposes.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        userService.softDelete(id);
        return ApiResponse.noContent("User deleted successfully");
    }
}
