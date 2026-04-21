package com.qms.module.dms.controller;

import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.dms.dto.request.*;
import com.qms.module.dms.dto.response.*;
import com.qms.module.dms.enums.DocumentCategory;
import com.qms.module.dms.enums.DocumentStatus;
import com.qms.module.dms.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * DMS REST API — Document lifecycle: upload → review → approve → publish → download.
 *
 * Upload pattern (multipart/form-data):
 *   Part "file"     — the binary file
 *   Part "metadata" — JSON-encoded UploadRequest with Content-Type: application/json
 *
 * Download pattern:
 *   Local storage  → file streamed through this endpoint (Content-Disposition: attachment)
 *   Cloud storage  → HTTP 200 + X-Download-URL header containing the pre-signed URL
 *                    The client should redirect to the signed URL for the actual download.
 */
@RestController
@RequestMapping("/api/v1/dms/documents")
@RequiredArgsConstructor
@Tag(name = "DMS — Documents",
     description = "Upload, version, approve, publish, and download controlled documents")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;

    // ═══════════════════════════════════════════════════════════
    // SEARCH & LIST
    // ═══════════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search documents",
               description = "All filters are optional and combinable. "
                           + "Only EFFECTIVE documents are visible to EMPLOYEE role. "
                           + "DOC_CONTROLLER and above see all statuses.")
    public ResponseEntity<ApiResponse<PageResponse<DocumentResponse>>> search(
            @RequestParam(required = false) DocumentStatus   status,
            @RequestParam(required = false) DocumentCategory category,
            @RequestParam(required = false) String           department,
            @RequestParam(required = false) Long             ownerId,
            @Parameter(description = "Full-text: title, doc number, tags")
            @RequestParam(required = false) String           search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(documentService.search(status, category, department,
                ownerId, search, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get document metadata by database ID")
    public ResponseEntity<ApiResponse<DocumentResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(documentService.getById(id));
    }

    @GetMapping("/number/{docNumber}/current")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the current EFFECTIVE version of a document by doc number",
               description = "Returns 404 if no EFFECTIVE version exists.")
    public ResponseEntity<ApiResponse<DocumentResponse>> getCurrentVersion(
            @PathVariable String docNumber) {
        return ApiResponse.ok(documentService.getCurrentVersion(docNumber));
    }

    @GetMapping("/number/{docNumber}/versions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','DOC_CONTROLLER','AUDITOR')")
    @Operation(summary = "Get the complete version history of a document")
    public ResponseEntity<ApiResponse<List<VersionSummaryResponse>>> getVersionHistory(
            @PathVariable String docNumber) {
        return ApiResponse.ok(documentService.getVersionHistory(docNumber));
    }

    @GetMapping("/{id}/approvals")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','DOC_CONTROLLER','AUDITOR')")
    @Operation(summary = "Get all approval records for a document")
    public ResponseEntity<ApiResponse<List<ApprovalResponse>>> getApprovals(
            @PathVariable Long id) {
        return ApiResponse.ok(documentService.getApprovals(id));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','DOC_CONTROLLER','AUDITOR')")
    @Operation(summary = "DMS dashboard KPIs",
               description = "Returns document counts by status, expiring soon, and due for review.")
    public ResponseEntity<ApiResponse<DocumentStatsResponse>> getStats() {
        return ApiResponse.ok(documentService.getStats());
    }

    // ═══════════════════════════════════════════════════════════
    // UPLOAD — new document
    // ═══════════════════════════════════════════════════════════

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','DOC_CONTROLLER')")
    @Operation(
        summary = "Upload a new document",
        description = """
            **Multipart request with two parts:**
            - `file` — the binary file (PDF, Word, Excel, etc.)
            - `metadata` — JSON body with title, category, department, dates etc.

            **Example with curl:**
            ```bash
            curl -X POST /api/v1/dms/documents \\
              -H "Authorization: Bearer $TOKEN" \\
              -F "file=@/path/to/sop.pdf;type=application/pdf" \\
              -F 'metadata={"title":"Cleaning SOP","category":"SOP"};type=application/json'
            ```

            The returned document will be in **DRAFT** status.
            Submit it for review using `POST /{id}/submit`.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
            description = "Document uploaded — status is DRAFT"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Invalid file type, size exceeded, or validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
            description = "Insufficient role")
    })
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestPart("file")     MultipartFile  file,
            @RequestPart("metadata") @Valid UploadRequest metadata) {
        return ApiResponse.created("Document uploaded successfully",
                documentService.upload(file, metadata));
    }

    // ═══════════════════════════════════════════════════════════
    // UPLOAD — new version of existing document
    // ═══════════════════════════════════════════════════════════

    @PostMapping(value = "/{id}/new-version", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','DOC_CONTROLLER')")
    @Operation(
        summary = "Upload a new version of an existing document",
        description = """
            Creates a new DRAFT version linked to the parent document.

            Version numbering:
            - Normal update: 1.0 → 1.1 → 1.2 (minor bump)
            - After rejection: 1.0 → 2.0 (major bump — content change required)

            The previous EFFECTIVE or APPROVED version is automatically **SUPERSEDED**.

            Provide a `changeSummary` in the metadata describing what changed.
            """
    )
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadNewVersion(
            @PathVariable Long id,
            @RequestPart("file")     MultipartFile  file,
            @RequestPart("metadata") @Valid UploadRequest metadata) {
        return ApiResponse.created("New version uploaded",
                documentService.uploadNewVersion(id, file, metadata));
    }

    // ═══════════════════════════════════════════════════════════
    // METADATA UPDATE
    // ═══════════════════════════════════════════════════════════

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','DOC_CONTROLLER')")
    @Operation(summary = "Update document metadata",
               description = "Only DRAFT documents can be updated. "
                           + "To change the file content, upload a new version.")
    public ResponseEntity<ApiResponse<DocumentResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentRequest request) {
        return ApiResponse.ok("Document updated", documentService.updateMetadata(id, request));
    }

    // ═══════════════════════════════════════════════════════════
    // WORKFLOW TRANSITIONS
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','DOC_CONTROLLER')")
    @Operation(summary = "Submit a DRAFT document for approval review",
               description = "Transitions: DRAFT → UNDER_REVIEW")
    public ResponseEntity<ApiResponse<DocumentResponse>> submit(
            @PathVariable Long id,
            @RequestParam(required = false) String comments) {
        return ApiResponse.ok("Document submitted for review",
                documentService.submit(id, comments));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Approve a document under review",
               description = "Transitions: UNDER_REVIEW → APPROVED (when quorum is met). "
                           + "If multiple approvers are required, the document remains UNDER_REVIEW until quorum.")
    public ResponseEntity<ApiResponse<DocumentResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalRequest request) {
        return ApiResponse.ok("Document approved", documentService.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER')")
    @Operation(summary = "Reject a document under review",
               description = "Transitions: UNDER_REVIEW → REJECTED. "
                           + "The author must upload a corrected new version.")
    public ResponseEntity<ApiResponse<DocumentResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalRequest request) {
        return ApiResponse.ok("Document rejected", documentService.reject(id, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','DOC_CONTROLLER')")
    @Operation(summary = "Publish an APPROVED document, making it EFFECTIVE",
               description = "Transitions: APPROVED → EFFECTIVE. "
                           + "Any previous EFFECTIVE version is automatically SUPERSEDED.")
    public ResponseEntity<ApiResponse<DocumentResponse>> publish(
            @PathVariable Long id,
            @Valid @RequestBody PublishRequest request) {
        return ApiResponse.ok("Document is now effective", documentService.publish(id, request));
    }

    @PostMapping("/{id}/obsolete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','DOC_CONTROLLER')")
    @Operation(summary = "Retire an EFFECTIVE document",
               description = "Transitions: EFFECTIVE → OBSOLETE. A reason is mandatory.")
    public ResponseEntity<ApiResponse<DocumentResponse>> obsolete(
            @PathVariable Long id,
            @Valid @RequestBody ObsoleteRequest request) {
        return ApiResponse.ok("Document marked obsolete", documentService.obsolete(id, request));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','DOC_CONTROLLER')")
    @Operation(summary = "Withdraw a DRAFT or UNDER_REVIEW document before it is approved",
               description = "Transitions: DRAFT|UNDER_REVIEW → WITHDRAWN")
    public ResponseEntity<ApiResponse<DocumentResponse>> withdraw(@PathVariable Long id) {
        return ApiResponse.ok("Document withdrawn", documentService.withdraw(id));
    }

    // ═══════════════════════════════════════════════════════════
    // DOWNLOAD
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Download a document file",
        description = """
            **Local storage:** streams the file directly (Content-Disposition: attachment).

            **Cloud storage (S3/Azure):** returns HTTP 200 with the header:
            `X-Download-URL: https://s3.amazonaws.com/...?AWSAccessKeyId=...`
            The client should perform a GET to that URL to download the file directly
            from cloud storage (bypasses the application server for efficiency).

            Download events are always logged for GxP compliance.
            Controlled documents require acknowledgement via `POST /{id}/acknowledge`.
            """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
        content = @Content(mediaType = "application/octet-stream",
        schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<InputStreamResource> download(
            @PathVariable Long id,
            HttpServletRequest request) {
        return documentService.download(id, request);
    }

    @PostMapping("/downloads/{downloadLogId}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Acknowledge reading a controlled document",
               description = "Required for controlled documents. "
                           + "The downloadLogId is returned in the download log.")
    public ResponseEntity<ApiResponse<Void>> acknowledge(
            @PathVariable Long downloadLogId) {
        documentService.acknowledge(downloadLogId);
        return ApiResponse.noContent("Document acknowledged");
    }

    // ═══════════════════════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════════════════════

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Soft-delete a document",
               description = "EFFECTIVE documents cannot be deleted — obsolete them first. "
                           + "The file is retained in storage for audit purposes.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        documentService.softDelete(id);
        return ApiResponse.noContent("Document deleted");
    }
}
