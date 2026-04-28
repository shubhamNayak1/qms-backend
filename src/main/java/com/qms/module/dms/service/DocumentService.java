package com.qms.module.dms.service;

import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.dms.dto.request.*;
import com.qms.module.dms.dto.response.*;
import com.qms.module.dms.entity.Document;
import com.qms.module.dms.entity.DocumentApproval;
import com.qms.module.dms.entity.DocumentDownloadLog;
import com.qms.module.dms.enums.DocumentCategory;
import com.qms.module.dms.enums.DocumentStatus;
import com.qms.module.dms.repository.*;
import com.qms.module.dms.service.storage.StorageService;
import com.qms.module.dms.workflow.DocumentWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.audit.context.AuditContext;
import com.qms.module.audit.context.AuditContextHolder;
import com.qms.module.audit.service.AuditValueSerializer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository            documentRepository;
    private final DocumentApprovalRepository    approvalRepository;
    private final DocumentDownloadLogRepository downloadLogRepository;
    private final StorageService                storageService;
    private final FileValidator                 fileValidator;
    private final DocumentWorkflowService       workflowService;
    private final AuditValueSerializer          auditSerializer;

    private static final DateTimeFormatter DOC_NUM_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    @Value("${dms.download.signed-url-ttl-seconds:3600}")
    private int signedUrlTtl;

    @Value("${dms.versioning.warning-days:30}")
    private int expiryWarningDays;

    // ─── Upload (new document) ────────────────────────────────

    @Audited(action = AuditAction.UPLOAD, module = AuditModule.DOCUMENT, entityType = "Document", description = "Document record created")
    @Transactional
    public DocumentResponse upload(MultipartFile file, UploadRequest req) {
        // 1. Validate file: MIME, size, virus scan
        String mimeType  = fileValidator.validateAndDetectMimeType(file);
        String checksum  = fileValidator.computeChecksum(file);

        Authentication auth = currentAuth();
        String username = auth != null ? auth.getName() : "SYSTEM";

        // 2. Generate doc number and store file
        String docNumber   = generateDocNumber(req.getCategory());
        String storageKey  = storageService.store(file, docNumber, "1.0");

        // 3. Build entity
        Document doc = Document.builder()
                .docNumber(docNumber)
                .version("1.0")
                .majorVersion(1)
                .minorVersion(0)
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory())
                .status(DocumentStatus.DRAFT)
                .accessLevel(req.getAccessLevel() != null
                        ? req.getAccessLevel()
                        : com.qms.module.dms.enums.AccessLevel.PUBLIC)
                .department(req.getDepartment())
                .tags(req.getTags())
                .authorId(extractUserId(auth))
                .authorName(username)
                .ownerId(req.getOwnerId() != null ? req.getOwnerId() : extractUserId(auth))
                .ownerName(username)
                .originalFilename(file.getOriginalFilename())
                .storageKey(storageKey)
                .mimeType(mimeType)
                .fileSizeBytes(file.getSize())
                .sha256Checksum(checksum)
                .effectiveDate(req.getEffectiveDate())
                .expiryDate(req.getExpiryDate())
                .reviewDate(req.getReviewDate())
                .changeSummary(req.getChangeSummary())
                .isControlled(req.getIsControlled() != null ? req.getIsControlled() : true)
                .downloadCount(0L)
                .build();

        Document saved = documentRepository.save(doc);
        log.info("Document uploaded: {} v1.0 by {}", docNumber, username);
        return toResponse(saved);
    }

    // ─── Upload new version ───────────────────────────────────

    @Audited(action = AuditAction.UPLOAD, module = AuditModule.DOCUMENT, entityType = "Document", entityIdArgIndex = 0)
    @Transactional
    public DocumentResponse uploadNewVersion(Long parentId, MultipartFile file,
                                              UploadRequest req) {
        Document parent = findById(parentId);

        if (parent.isTerminal()) {
            throw AppException.badRequest(
                    "Cannot create a new version of a " + parent.getStatus() + " document");
        }

        String mimeType  = fileValidator.validateAndDetectMimeType(file);
        String checksum  = fileValidator.computeChecksum(file);
        Authentication auth = currentAuth();
        String username = auth != null ? auth.getName() : "SYSTEM";

        // Determine new version — major bump if parent was rejected
        int newMajor = parent.getMajorVersion();
        int newMinor = parent.getMinorVersion() + 1;
        if (parent.getStatus() == DocumentStatus.REJECTED) {
            newMajor++;
            newMinor = 0;
        }
        String newVersion = newMajor + "." + newMinor;

        String storageKey = storageService.store(file, parent.getDocNumber(), newVersion);

        // Supersede the parent if it is EFFECTIVE or APPROVED
        workflowService.supersede(parent);
        documentRepository.save(parent);

        Document newDoc = Document.builder()
                .docNumber(parent.getDocNumber())
                .version(newVersion)
                .majorVersion(newMajor)
                .minorVersion(newMinor)
                .title(req.getTitle() != null ? req.getTitle() : parent.getTitle())
                .description(req.getDescription() != null ? req.getDescription() : parent.getDescription())
                .category(parent.getCategory())
                .status(DocumentStatus.DRAFT)
                .accessLevel(parent.getAccessLevel())
                .department(parent.getDepartment())
                .tags(req.getTags() != null ? req.getTags() : parent.getTags())
                .authorId(extractUserId(auth))
                .authorName(username)
                .ownerId(parent.getOwnerId())
                .ownerName(parent.getOwnerName())
                .originalFilename(file.getOriginalFilename())
                .storageKey(storageKey)
                .mimeType(mimeType)
                .fileSizeBytes(file.getSize())
                .sha256Checksum(checksum)
                .effectiveDate(req.getEffectiveDate())
                .expiryDate(req.getExpiryDate() != null ? req.getExpiryDate() : parent.getExpiryDate())
                .reviewDate(req.getReviewDate() != null ? req.getReviewDate() : parent.getReviewDate())
                .changeSummary(req.getChangeSummary())
                .isControlled(parent.getIsControlled())
                .parentId(parentId)
                .downloadCount(0L)
                .build();

        Document saved = documentRepository.save(newDoc);
        log.info("New version uploaded: {} v{} by {}", saved.getDocNumber(), newVersion, username);
        return toResponse(saved);
    }

    // ─── Queries ──────────────────────────────────────────────

    public PageResponse<DocumentResponse> search(DocumentStatus status,
                                                  DocumentCategory category,
                                                  String department, Long ownerId,
                                                  String search, int page, int size) {
        // Pre-build the wildcard pattern in Java so the repository query receives a typed
        // varchar value — avoids Hibernate 6 binding null as bytea in PostgreSQL.
        String searchPattern = (search != null && !search.isBlank())
                ? "%" + search.toLowerCase() + "%" : null;
        return PageResponse.of(
                documentRepository.search(status, category, department, ownerId, searchPattern,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
                        .map(this::toResponse));
    }

    public DocumentResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public DocumentResponse getCurrentVersion(String docNumber) {
        return toResponse(documentRepository
                .findCurrentEffectiveByDocNumber(docNumber)
                .orElseThrow(() -> AppException.notFound(
                        "No effective document found with number: " + docNumber)));
    }

    public List<VersionSummaryResponse> getVersionHistory(String docNumber) {
        List<Document> versions = documentRepository.findAllVersionsByDocNumber(docNumber);
        if (versions.isEmpty()) {
            throw AppException.notFound("Document not found with number: " + docNumber);
        }
        String latest = versions.get(0).getVersion();
        return versions.stream()
                .map(d -> VersionSummaryResponse.builder()
                        .id(d.getId()).version(d.getVersion()).status(d.getStatus())
                        .changeSummary(d.getChangeSummary()).authorName(d.getAuthorName())
                        .createdAt(d.getCreatedAt()).approvedAt(d.getApprovedAt())
                        .approvedByName(d.getApprovedByName())
                        .isCurrent(d.getVersion().equals(latest))
                        .build())
                .toList();
    }

    public List<ApprovalResponse> getApprovals(Long documentId) {
        findById(documentId); // ensure document exists
        return approvalRepository.findByDocument_IdOrderByCreatedAtAsc(documentId)
                .stream().map(this::toApprovalResponse).toList();
    }

    // ─── Metadata update ──────────────────────────────────────

    @Audited(action = AuditAction.UPDATE, module = AuditModule.DOCUMENT, entityType = "Document", entityIdArgIndex = 0)
    @Transactional
    public DocumentResponse updateMetadata(Long id, UpdateDocumentRequest req) {
        Document doc = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(doc)))
                .build());
        if (doc.getStatus() != DocumentStatus.DRAFT) {
            throw AppException.badRequest(
                    "Only DRAFT documents can have their metadata updated. Current: " + doc.getStatus());
        }
        if (req.getTitle()        != null) doc.setTitle(req.getTitle());
        if (req.getDescription()  != null) doc.setDescription(req.getDescription());
        if (req.getDepartment()   != null) doc.setDepartment(req.getDepartment());
        if (req.getTags()         != null) doc.setTags(req.getTags());
        if (req.getAccessLevel()  != null) doc.setAccessLevel(req.getAccessLevel());
        if (req.getEffectiveDate() != null) doc.setEffectiveDate(req.getEffectiveDate());
        if (req.getExpiryDate()   != null) doc.setExpiryDate(req.getExpiryDate());
        if (req.getReviewDate()   != null) doc.setReviewDate(req.getReviewDate());
        if (req.getChangeSummary() != null) doc.setChangeSummary(req.getChangeSummary());
        if (req.getIsControlled() != null) doc.setIsControlled(req.getIsControlled());
        if (req.getOwnerId()      != null) doc.setOwnerId(req.getOwnerId());
        return toResponse(documentRepository.save(doc));
    }

    // ─── Workflow transitions ─────────────────────────────────

    @Audited(action = AuditAction.SUBMIT, module = AuditModule.DOCUMENT, entityType = "Document", entityIdArgIndex = 0)
    @Transactional
    public DocumentResponse submit(Long id, String comments) {
        Document doc = findById(id);
        workflowService.submit(doc, comments);
        return toResponse(documentRepository.save(doc));
    }

    @Audited(action = AuditAction.APPROVE, module = AuditModule.DOCUMENT, entityType = "Document", entityIdArgIndex = 0)
    @Transactional
    public DocumentResponse approve(Long id, ApprovalRequest req) {
        Document doc = findById(id);
        workflowService.approve(doc, req.getComments());
        return toResponse(documentRepository.save(doc));
    }

    @Audited(action = AuditAction.REJECT, module = AuditModule.DOCUMENT, entityType = "Document", entityIdArgIndex = 0)
    @Transactional
    public DocumentResponse reject(Long id, ApprovalRequest req) {
        Document doc = findById(id);
        workflowService.reject(doc, req.getComments());
        return toResponse(documentRepository.save(doc));
    }

    @Audited(action = AuditAction.PUBLISH, module = AuditModule.DOCUMENT, entityType = "Document", entityIdArgIndex = 0)
    @Transactional
    public DocumentResponse publish(Long id, PublishRequest req) {
        Document doc = findById(id);
        workflowService.publish(doc, req.getEffectiveDate());
        return toResponse(documentRepository.save(doc));
    }

    @Audited(action = AuditAction.OBSOLETE, module = AuditModule.DOCUMENT, entityType = "Document", entityIdArgIndex = 0)
    @Transactional
    public DocumentResponse obsolete(Long id, ObsoleteRequest req) {
        Document doc = findById(id);
        workflowService.obsolete(doc, req.getReason());
        return toResponse(documentRepository.save(doc));
    }

    @Audited(action = AuditAction.UPDATE, module = AuditModule.DOCUMENT, entityType = "Document", entityIdArgIndex = 0)
    @Transactional
    public DocumentResponse withdraw(Long id) {
        Document doc = findById(id);
        workflowService.withdraw(doc);
        return toResponse(documentRepository.save(doc));
    }

    // ─── Download ─────────────────────────────────────────────

    @Audited(action = AuditAction.DOWNLOAD, module = AuditModule.DOCUMENT, entityType = "Document", entityIdArgIndex = 0)
    @Transactional
    public ResponseEntity<InputStreamResource> download(Long id, HttpServletRequest request) {
        Document doc = findById(id);

        if (doc.isTerminal()) {
            throw AppException.badRequest(
                    "Cannot download a " + doc.getStatus() + " document");
        }

        // For cloud storage: redirect to signed URL instead of streaming through app
        String signedUrl = storageService.getSignedUrl(doc.getStorageKey(), signedUrlTtl);
        if (signedUrl != null) {
            // Cloud: log the download and return redirect URL in header
            recordDownload(doc, request);
            documentRepository.incrementDownloadCount(id);
            return ResponseEntity.ok()
                    .header("X-Download-URL", signedUrl)
                    .build();
        }

        // Local: stream the file through the application
        InputStream stream = storageService.retrieve(doc.getStorageKey());
        recordDownload(doc, request);
        documentRepository.incrementDownloadCount(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(doc.getOriginalFilename(), StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .contentLength(doc.getFileSizeBytes())
                .body(new InputStreamResource(stream));
    }

    @Transactional
    public void acknowledge(Long downloadLogId) {
        Authentication auth = currentAuth();
        Long userId = extractUserId(auth);
        DocumentDownloadLog log = downloadLogRepository
                .findByIdAndUserId(downloadLogId, userId)
                .orElseThrow(() -> AppException.notFound("Download record", downloadLogId));
        log.setAcknowledged(true);
        log.setAcknowledgedAt(LocalDateTime.now());
        downloadLogRepository.save(log);
    }

    // ─── Stats dashboard ──────────────────────────────────────

    public DocumentStatsResponse getStats() {
        LocalDate today = LocalDate.now();
        LocalDate warningDate = today.plusDays(expiryWarningDays);

        List<Document> expiringSoon = documentRepository.findExpiringSoon(today, warningDate);
        List<Document> dueForReview = documentRepository.findDueForReview(today, warningDate);
        List<Document> expired      = documentRepository.findExpired(today);

        return DocumentStatsResponse.builder()
                .generatedAt(LocalDateTime.now())
                .draftCount(documentRepository.countByStatusAndIsDeletedFalse(DocumentStatus.DRAFT))
                .underReviewCount(documentRepository.countByStatusAndIsDeletedFalse(DocumentStatus.UNDER_REVIEW))
                .approvedCount(documentRepository.countByStatusAndIsDeletedFalse(DocumentStatus.APPROVED))
                .effectiveCount(documentRepository.countByStatusAndIsDeletedFalse(DocumentStatus.EFFECTIVE))
                .obsoleteCount(documentRepository.countByStatusAndIsDeletedFalse(DocumentStatus.OBSOLETE))
                .totalCount(documentRepository.countByStatusAndIsDeletedFalse(DocumentStatus.EFFECTIVE)
                        + documentRepository.countByStatusAndIsDeletedFalse(DocumentStatus.DRAFT))
                .expiringSoonCount(expiringSoon.size())
                .expiredCount(expired.size())
                .dueForReviewCount(dueForReview.size())
                .expiringSoon(expiringSoon.stream().map(this::toResponse).toList())
                .dueForReview(dueForReview.stream().map(this::toResponse).toList())
                .build();
    }

    @Audited(action = AuditAction.DELETE, module = AuditModule.DOCUMENT, entityType = "Document", entityIdArgIndex = 0, captureNewValue = false, description = "Document record deleted")
    @Transactional
    public void softDelete(Long id) {
        Document doc = findById(id);
        AuditContextHolder.set(AuditContext.builder()
                .oldValue(auditSerializer.serialize(toResponse(doc)))
                .build());
        if (doc.getStatus() == DocumentStatus.EFFECTIVE) {
            throw AppException.badRequest("Cannot delete an EFFECTIVE document. Obsolete it first.");
        }
        doc.setIsDeleted(true);
        // Optionally remove from storage (disabled for compliance — keeps evidence)
        // storageService.delete(doc.getStorageKey());
        documentRepository.save(doc);
        log.info("Document soft-deleted: {} v{}", doc.getDocNumber(), doc.getVersion());
    }

    // ─── Helpers ──────────────────────────────────────────────

    private Document findById(Long id) {
        return documentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> AppException.notFound("Document", id));
    }

    private String generateDocNumber(DocumentCategory category) {
        String prefix = "DOC-" + category.name().substring(0, Math.min(3, category.name().length()))
                + "-" + DOC_NUM_FMT.format(LocalDate.now()) + "-";
        // Use the per-prefix max rather than a global count — avoids cross-category collisions.
        // The unique constraint on doc_number guards the narrow concurrent-insert window.
        int nextSeq = documentRepository.findMaxSequenceForPrefix(prefix, prefix.length() + 1) + 1;
        return prefix + String.format("%04d", nextSeq);
    }

    private void recordDownload(Document doc, HttpServletRequest request) {
        Authentication auth = currentAuth();
        String forwarded = request != null ? request.getHeader("X-Forwarded-For") : null;
        String ip = forwarded != null ? forwarded.split(",")[0].trim()
                : (request != null ? request.getRemoteAddr() : null);

        DocumentDownloadLog entry = DocumentDownloadLog.builder()
                .documentId(doc.getId())
                .docNumber(doc.getDocNumber())
                .version(doc.getVersion())
                .userId(extractUserId(auth))
                .username(auth != null ? auth.getName() : "ANONYMOUS")
                .ipAddress(ip)
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .acknowledged(!doc.getIsControlled()) // non-controlled docs auto-acknowledged
                .build();
        downloadLogRepository.save(entry);
    }

    private Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null) return null;
        if (auth.getPrincipal() instanceof com.qms.security.UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    private Set<DocumentStatus> allowedTransitions(DocumentStatus current) {
        return switch (current) {
            case DRAFT        -> Set.of(DocumentStatus.UNDER_REVIEW, DocumentStatus.WITHDRAWN);
            case UNDER_REVIEW -> Set.of(DocumentStatus.APPROVED, DocumentStatus.REJECTED);
            case APPROVED     -> Set.of(DocumentStatus.EFFECTIVE);
            case REJECTED     -> Set.of(DocumentStatus.UNDER_REVIEW);
            case EFFECTIVE    -> Set.of(DocumentStatus.OBSOLETE);
            default           -> Set.of();
        };
    }

    // ─── Mapping ──────────────────────────────────────────────

    private DocumentResponse toResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .docNumber(doc.getDocNumber())
                .version(doc.getVersion())
                .fullVersion(doc.getFullVersion())
                .majorVersion(doc.getMajorVersion())
                .minorVersion(doc.getMinorVersion())
                .title(doc.getTitle())
                .description(doc.getDescription())
                .category(doc.getCategory())
                .status(doc.getStatus())
                .accessLevel(doc.getAccessLevel())
                .department(doc.getDepartment())
                .tags(doc.getTags())
                .ownerId(doc.getOwnerId())
                .ownerName(doc.getOwnerName())
                .authorId(doc.getAuthorId())
                .authorName(doc.getAuthorName())
                .originalFilename(doc.getOriginalFilename())
                .mimeType(doc.getMimeType())
                .fileSizeBytes(doc.getFileSizeBytes())
                .fileSizeHuman(humanReadableSize(doc.getFileSizeBytes()))
                .sha256Checksum(doc.getSha256Checksum())
                .effectiveDate(doc.getEffectiveDate())
                .expiryDate(doc.getExpiryDate())
                .reviewDate(doc.getReviewDate())
                .publishedAt(doc.getPublishedAt())
                .obsoletedAt(doc.getObsoletedAt())
                .approvedById(doc.getApprovedById())
                .approvedByName(doc.getApprovedByName())
                .approvedAt(doc.getApprovedAt())
                .approvalComments(doc.getApprovalComments())
                .rejectionReason(doc.getRejectionReason())
                .parentId(doc.getParentId())
                .changeSummary(doc.getChangeSummary())
                .isControlled(doc.getIsControlled())
                .downloadCount(doc.getDownloadCount())
                .expired(doc.isExpired())
                .expiringSoon(doc.isExpiringSoon(expiryWarningDays))
                .allowedTransitions(allowedTransitions(doc.getStatus()))
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .createdBy(doc.getCreatedBy())
                .updatedBy(doc.getUpdatedBy())
                .build();
    }

    private ApprovalResponse toApprovalResponse(DocumentApproval a) {
        return ApprovalResponse.builder()
                .id(a.getId()).approverId(a.getApproverId()).approverName(a.getApproverName())
                .approverRole(a.getApproverRole()).decision(a.getDecision())
                .comments(a.getComments()).decidedAt(a.getDecidedAt())
                .reviewCycle(a.getReviewCycle()).createdAt(a.getCreatedAt())
                .build();
    }

    private String humanReadableSize(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
