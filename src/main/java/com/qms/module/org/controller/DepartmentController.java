package com.qms.module.org.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.org.dto.request.DepartmentRequest;
import com.qms.module.org.dto.response.DepartmentResponse;
import com.qms.module.org.service.DepartmentService;
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
@RequestMapping("/api/v1/org/departments")
@RequiredArgsConstructor
@Tag(name = "Org Structure — Departments",
     description = "Manage departments and sub-departments. Drives QMS workflow gating.")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List every active department (flat list)")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> list() {
        return ApiResponse.ok(departmentService.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(departmentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create a new department or sub-department")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
            @Valid @RequestBody DepartmentRequest req) {
        return ApiResponse.created("Department created", departmentService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update department (name, code, HOD, parent, type)")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest req) {
        return ApiResponse.ok("Department updated", departmentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Soft-delete a department",
               description = "Sub-departments are re-parented to this dept's parent.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        departmentService.softDelete(id);
        return ApiResponse.noContent("Department deleted");
    }
}
