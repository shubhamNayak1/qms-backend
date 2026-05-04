package com.qms.module.org.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.org.dto.response.OrgTreeResponse;
import com.qms.module.org.service.OrgTreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/org/tree")
@RequiredArgsConstructor
@Tag(name = "Org Structure — Tree", description = "Top-down nested org tree (Site → Departments → Sub-Departments → Users)")
@SecurityRequirement(name = "bearerAuth")
public class OrgTreeController {

    private final OrgTreeService orgTreeService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Build the full organisational tree",
               description = """
                   Returns a single nested object suitable for a tree
                   visualisation. Includes the Site Head, every department
                   with its HOD and members, recursively into sub-departments.
                   Each user node carries license status, initials, and
                   reviewer flags so the UI can render badges directly.
                   """)
    public ResponseEntity<ApiResponse<OrgTreeResponse>> tree() {
        return ApiResponse.ok(orgTreeService.build());
    }
}
