package com.qms.module.qms.common.controller;

import com.qms.common.enums.QmsRecordType;
import com.qms.common.exception.AppException;
import com.qms.common.response.ApiResponse;
import com.qms.module.org.service.OrgSecurityService;
import com.qms.module.qms.common.entity.QmsRecordAttachment;
import com.qms.module.qms.common.repository.QmsRecordAttachmentRepository;
import com.qms.module.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic local-file attachment endpoint (Round-2 A1).
 *
 *  POST   /api/v1/qms/attachments/upload          — store a local file blob
 *  GET    /api/v1/qms/attachments/{id}/metadata   — JSON metadata
 *  GET    /api/v1/qms/attachments/{id}/download   — stream the raw bytes
 *
 * Files are capped at 10 MB to keep the qms_record_attachments table small.
 * Beyond that, the user should check the file into DMS first and reference
 * the DMS document id from the QMS record instead.
 */
@RestController
@RequestMapping("/api/v1/qms/attachments")
@RequiredArgsConstructor
@Tag(name = "QMS Attachments", description = "Round-2 A1 — local file upload alongside the DMS link slot")
public class QmsRecordAttachmentController {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB

    private final QmsRecordAttachmentRepository repository;
    private final OrgSecurityService orgSecurity;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload a local file as a QMS record attachment")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "recordType", required = false) String recordType,
            @RequestPart(value = "recordId", required = false) String recordIdStr) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("No file supplied.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw AppException.badRequest(
                    "File is " + (file.getSize() / 1024 / 1024)
                    + " MB — limit is 10 MB. Check the file into DMS first and reference its document id instead.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw AppException.badRequest("Failed to read uploaded file: " + e.getMessage());
        }

        QmsRecordType type = null;
        if (recordType != null && !recordType.isBlank()) {
            try {
                type = QmsRecordType.valueOf(recordType);
            } catch (IllegalArgumentException e) {
                throw AppException.badRequest("Unknown record type: " + recordType);
            }
        }
        Long recordId = null;
        if (recordIdStr != null && !recordIdStr.isBlank()) {
            try {
                recordId = Long.valueOf(recordIdStr);
            } catch (NumberFormatException e) {
                throw AppException.badRequest("Invalid record id: " + recordIdStr);
            }
        }

        User actor = orgSecurity.currentUser().orElse(null);
        QmsRecordAttachment att = QmsRecordAttachment.builder()
                .recordType(type)
                .recordId(recordId)
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes((long) bytes.length)
                .data(bytes)
                .uploadedById(actor != null ? actor.getId() : null)
                .uploadedByName(actor != null ? actor.getFullName() : null)
                .build();
        QmsRecordAttachment saved = repository.save(att);

        Map<String, Object> out = new HashMap<>();
        out.put("id", saved.getId());
        // The ref string callers should store on the parent record. The
        // resolver in ChangeControlService recognises this prefix.
        out.put("attachmentRef", "QMS-ATT-" + saved.getId());
        out.put("fileName",      saved.getFileName());
        out.put("contentType",   saved.getContentType());
        out.put("sizeBytes",     saved.getSizeBytes());
        out.put("uploadedAt",    saved.getUploadedAt());
        out.put("uploadedBy",    saved.getUploadedByName());
        return ApiResponse.ok("Uploaded", out);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List attachments for a record (Round-2 H4 stage panel)")
    public ResponseEntity<ApiResponse<java.util.List<Map<String, Object>>>> list(
            @RequestParam String recordType,
            @RequestParam Long recordId) {
        QmsRecordType type;
        try {
            type = QmsRecordType.valueOf(recordType);
        } catch (IllegalArgumentException e) {
            throw AppException.badRequest("Unknown record type: " + recordType);
        }
        java.util.List<Map<String, Object>> rows = repository
                .findAllByRecordTypeAndRecordIdAndIsDeletedFalseOrderByUploadedAtAsc(type, recordId)
                .stream()
                .map(a -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", a.getId());
                    m.put("fileName", a.getFileName());
                    m.put("contentType", a.getContentType());
                    m.put("sizeBytes", a.getSizeBytes());
                    m.put("uploadedAt", a.getUploadedAt());
                    m.put("uploadedBy", a.getUploadedByName());
                    m.put("attachmentRef", "QMS-ATT-" + a.getId());
                    return m;
                })
                .toList();
        return ApiResponse.ok(rows);
    }

    @GetMapping("/{id}/metadata")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get attachment metadata")
    public ResponseEntity<ApiResponse<Map<String, Object>>> metadata(@PathVariable Long id) {
        QmsRecordAttachment a = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Attachment not found: " + id));
        Map<String, Object> out = new HashMap<>();
        out.put("id", a.getId());
        out.put("fileName",    a.getFileName());
        out.put("contentType", a.getContentType());
        out.put("sizeBytes",   a.getSizeBytes());
        out.put("uploadedAt",  a.getUploadedAt());
        out.put("uploadedBy",  a.getUploadedByName());
        out.put("attachmentRef", "QMS-ATT-" + a.getId());
        return ApiResponse.ok(out);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download attachment bytes")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        QmsRecordAttachment a = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Attachment not found: " + id));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(a.getContentType() != null
                ? MediaType.parseMediaType(a.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(org.springframework.http.ContentDisposition
                .attachment().filename(a.getFileName()).build());
        headers.setContentLength(a.getSizeBytes());
        return new ResponseEntity<>(a.getData(), headers, org.springframework.http.HttpStatus.OK);
    }
}
