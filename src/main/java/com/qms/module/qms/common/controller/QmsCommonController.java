package com.qms.module.qms.common.controller;

import com.qms.common.enums.QmsRecordType;
import com.qms.common.response.ApiResponse;
import com.qms.module.qms.common.dto.request.QmsDepartmentCommentRequest;
import com.qms.module.qms.common.dto.request.QmsLineItemRequest;
import com.qms.module.qms.common.dto.request.TargetDateExtensionDecision;
import com.qms.module.qms.common.dto.request.TargetDateExtensionRequest;
import com.qms.module.qms.common.dto.response.QmsDepartmentCommentResponse;
import com.qms.module.qms.common.dto.response.QmsLineItemResponse;
import com.qms.module.qms.common.dto.response.TargetDateExtensionResponse;
import com.qms.module.qms.common.service.QmsDepartmentCommentService;
import com.qms.module.qms.common.service.QmsLineItemService;
import com.qms.module.qms.common.service.TargetDateExtensionService;
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
 * Common QMS endpoints shared by every sub-module (CAPA, Deviation,
 * Incident, Change Control, Market Complaint).
 *
 * Routes are scoped by record type so the same UI components and APIs
 * work uniformly across the whole module:
 *
 *   /api/v1/qms/{recordType}/{recordId}/line-items
 *   /api/v1/qms/{recordType}/{recordId}/department-comments
 *   /api/v1/qms/{recordType}/{recordId}/target-date-extension
 *
 * recordType is the lower-case enum name: capa | deviation | incident |
 * change-control | market-complaint.
 */
@RestController
@RequestMapping("/api/v1/qms/{recordType}/{recordId}")
@RequiredArgsConstructor
@Tag(name = "QMS Common", description = "Cross-module QMS endpoints (line items, dept comments, target-date extension)")
@SecurityRequirement(name = "bearerAuth")
public class QmsCommonController {

    private final QmsLineItemService          lineItemService;
    private final QmsDepartmentCommentService deptCommentService;
    private final TargetDateExtensionService  extensionService;

    // ─────────────────────────────────────────────────────────
    //  Line items — repeating "existing/proposed/justification" rows
    // ─────────────────────────────────────────────────────────

    @GetMapping("/line-items")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List line items on a QMS record")
    public ResponseEntity<ApiResponse<List<QmsLineItemResponse>>> listLineItems(
            @PathVariable String recordType,
            @PathVariable Long   recordId) {
        return ApiResponse.ok(lineItemService.list(parseType(recordType), recordId));
    }

    @PostMapping("/line-items")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add a line item")
    public ResponseEntity<ApiResponse<QmsLineItemResponse>> createLineItem(
            @PathVariable String recordType,
            @PathVariable Long   recordId,
            @Valid @RequestBody  QmsLineItemRequest request) {
        return ApiResponse.created("Line item added",
                lineItemService.create(parseType(recordType), recordId, request));
    }

    @PutMapping("/line-items/{lineItemId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update a line item (used by Initiator and during verification)")
    public ResponseEntity<ApiResponse<QmsLineItemResponse>> updateLineItem(
            @PathVariable String recordType,
            @PathVariable Long   recordId,
            @PathVariable Long   lineItemId,
            @Valid @RequestBody  QmsLineItemRequest request) {
        return ApiResponse.ok("Line item updated",
                lineItemService.update(lineItemId, request));
    }

    @DeleteMapping("/line-items/{lineItemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteLineItem(
            @PathVariable String recordType,
            @PathVariable Long   recordId,
            @PathVariable Long   lineItemId) {
        lineItemService.delete(lineItemId);
        return ApiResponse.noContent("Line item removed");
    }

    // ─────────────────────────────────────────────────────────
    //  Department comments — fan-out comment requests
    // ─────────────────────────────────────────────────────────

    @GetMapping("/department-comments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List department-comment rows on a QMS record")
    public ResponseEntity<ApiResponse<List<QmsDepartmentCommentResponse>>> listDeptComments(
            @PathVariable String recordType,
            @PathVariable Long   recordId) {
        return ApiResponse.ok(deptCommentService.list(parseType(recordType), recordId));
    }

    @PostMapping("/department-comments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Request a comment from a specific department",
               description = "QA Reviewer / QA Head only. The dept's HOD will fill it in via PUT /department-comments/{id}.")
    public ResponseEntity<ApiResponse<QmsDepartmentCommentResponse>> requestDeptComment(
            @PathVariable String recordType,
            @PathVariable Long   recordId,
            @Valid @RequestBody  QmsDepartmentCommentRequest request) {
        return ApiResponse.created("Comment requested",
                deptCommentService.request(parseType(recordType), recordId, request));
    }

    @PutMapping("/department-comments/{commentRowId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fill in a department comment (HOD of the targeted department only)")
    public ResponseEntity<ApiResponse<QmsDepartmentCommentResponse>> fillDeptComment(
            @PathVariable String recordType,
            @PathVariable Long   recordId,
            @PathVariable Long   commentRowId,
            @Valid @RequestBody  QmsDepartmentCommentRequest request) {
        return ApiResponse.ok("Comment recorded",
                deptCommentService.fill(commentRowId, request));
    }

    // ─────────────────────────────────────────────────────────
    //  Target-date extension — inline approval workflow
    // ─────────────────────────────────────────────────────────

    @GetMapping("/target-date-extension")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Read the current target-date extension state")
    public ResponseEntity<ApiResponse<TargetDateExtensionResponse>> getExtension(
            @PathVariable String recordType,
            @PathVariable Long   recordId) {
        return ApiResponse.ok(extensionService.get(parseType(recordType), recordId));
    }

    @PostMapping("/target-date-extension")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Initiator requests an extension of the target completion date")
    public ResponseEntity<ApiResponse<TargetDateExtensionResponse>> requestExtension(
            @PathVariable String recordType,
            @PathVariable Long   recordId,
            @Valid @RequestBody  TargetDateExtensionRequest request) {
        return ApiResponse.created("Extension requested",
                extensionService.request(parseType(recordType), recordId, request));
    }

    @PostMapping("/target-date-extension/decide")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "HOD / QA Reviewer approves or rejects an extension request")
    public ResponseEntity<ApiResponse<TargetDateExtensionResponse>> decideExtension(
            @PathVariable String recordType,
            @PathVariable Long   recordId,
            @Valid @RequestBody  TargetDateExtensionDecision decision) {
        return ApiResponse.ok("Extension decision recorded",
                extensionService.decide(parseType(recordType), recordId, decision));
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Maps a URL slug to the QmsRecordType enum.
     * Accepts both kebab-case ("change-control") and underscore form
     * ("CHANGE_CONTROL") for caller flexibility.
     */
    private QmsRecordType parseType(String slug) {
        if (slug == null) {
            throw new IllegalArgumentException("recordType is required");
        }
        return QmsRecordType.valueOf(slug.toUpperCase().replace('-', '_'));
    }
}
