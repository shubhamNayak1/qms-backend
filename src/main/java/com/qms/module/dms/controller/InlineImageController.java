package com.qms.module.dms.controller;

import com.qms.common.exception.AppException;
import com.qms.common.response.ApiResponse;
import com.qms.module.dms.service.storage.StorageService;
import com.qms.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * InlineImageController — upload / serve images embedded inline in
 * rich-text fields (RichTextField's image button on the frontend).
 *
 * Rationale (R.2): Quill's default behaviour inlines images as base64
 * strings inside the HTML column — fine for prototypes, unsustainable
 * for pharma records that may carry dozens of images each. This
 * controller stores the file via the same StorageService the DMS
 * documents use and returns a URL the editor embeds in img src.
 *
 * Scope guard: these images are NOT first-class DMS documents. They
 * are inline attachments supporting a QMS narrative. No approval
 * workflow, no versioning, no DMS catalogue entry — just storage.
 *
 * Endpoints:
 *   POST /api/v1/dms/inline-images         — upload, returns {url}
 *   GET  /api/v1/dms/inline-images/{key}   — serve image bytes
 */
@RestController
@RequestMapping("/api/v1/dms/inline-images")
@RequiredArgsConstructor
@Tag(name = "DMS Inline Images",
     description = "Upload and serve rich-text inline images (QMS editors)")
@SecurityRequirement(name = "bearerAuth")
public class InlineImageController {

    private final StorageService storage;

    private static final long MAX_BYTES = 5L * 1024 * 1024;   // 5 MB
    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload an inline image for a rich-text field")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(
            @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw AppException.badRequest("Image too large (max 5 MB)");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) {
            throw AppException.badRequest("Only image files are accepted");
        }
        String actor = SecurityUtils.getCurrentUsername().orElse("anonymous");
        String docNumber = "inline-" + DAY_FMT.format(LocalDateTime.now());
        String version   = UUID.randomUUID().toString();
        String key = storage.store(file, docNumber, version);
        // Returned URL — the frontend inserts this exact string into the
        // editor as <img src="..."> so the stored HTML carries only a
        // link, not kilobytes of base64.
        String url = "/api/v1/dms/inline-images/" + key;
        return ApiResponse.ok("Uploaded",
                Map.of("url", url, "key", key, "uploadedBy", actor));
    }

    @GetMapping("/{key:.+}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Serve a previously uploaded inline image")
    public ResponseEntity<InputStreamResource> serve(@PathVariable String key) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(storage.retrieve(key)));
    }
}
