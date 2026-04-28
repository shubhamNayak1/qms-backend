package com.qms.module.lms.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.lms.dto.request.TniRequest;
import com.qms.module.lms.dto.response.TniResponse;
import com.qms.module.lms.service.ComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lms/tni")
@RequiredArgsConstructor
@Tag(name = "LMS — TNI", description = "Training Need Identification — auto-generated after induction approval")
@SecurityRequirement(name = "bearerAuth")
public class TniController {

    private final ComplianceService complianceService;

    @GetMapping("/enrollment/{enrollmentId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get TNI for a specific enrollment")
    public ResponseEntity<ApiResponse<TniResponse>> getByEnrollment(@PathVariable Long enrollmentId) {
        return ApiResponse.ok(complianceService.getTni(enrollmentId));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','HR','MANAGER')")
    @Operation(summary = "Get all TNI records for a user")
    public ResponseEntity<ApiResponse<List<TniResponse>>> getByUser(@PathVariable Long userId) {
        return ApiResponse.ok(complianceService.getTniByUser(userId));
    }

    @PutMapping("/enrollment/{enrollmentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','HR')")
    @Operation(summary = "Update TNI details (gaps, recommendations, JD)",
               description = "TNI is auto-generated at QA Head approval. Use this to update or enrich the content.")
    public ResponseEntity<ApiResponse<TniResponse>> update(
            @PathVariable Long enrollmentId,
            @RequestBody TniRequest req) {
        return ApiResponse.ok("TNI updated", complianceService.updateTni(enrollmentId, req));
    }
}
