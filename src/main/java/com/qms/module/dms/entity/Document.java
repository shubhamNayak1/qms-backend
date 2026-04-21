package com.qms.module.dms.entity;

import com.qms.common.base.BaseEntity;
import com.qms.module.dms.enums.AccessLevel;
import com.qms.module.dms.enums.DocumentCategory;
import com.qms.module.dms.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Document metadata entity.
 *
 * Design decisions:
 * ──────────────────
 * 1. This entity stores ONLY metadata — never the file bytes.
 *    File content lives in the storage backend (local FS / S3 / Azure).
 *    The storage_key column is the opaque path/key to locate the file.
 *
 * 2. Version family grouping:
 *    All versions of the same document share a doc_number.
 *    The parent_id links each version to its immediate predecessor.
 *    Querying by doc_number + ORDER BY version gives the full history.
 *
 * 3. Controlled distribution:
 *    is_controlled = true means the document is subject to
 *    version-controlled distribution and acknowledgement tracking.
 *
 * 4. Checksum:
 *    sha256_checksum is computed on upload and verified on download,
 *    providing tamper-evidence without relying on storage backend integrity.
 */
@Entity
@Table(
    name = "dms_documents",
    indexes = {
        @Index(name = "idx_doc_number",     columnList = "doc_number"),
        @Index(name = "idx_doc_status",     columnList = "status"),
        @Index(name = "idx_doc_category",   columnList = "category"),
        @Index(name = "idx_doc_department", columnList = "department"),
        @Index(name = "idx_doc_effective",  columnList = "effective_date"),
        @Index(name = "idx_doc_expiry",     columnList = "expiry_date"),
        @Index(name = "idx_doc_parent",     columnList = "parent_id"),
        @Index(name = "idx_doc_deleted",    columnList = "is_deleted")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document extends BaseEntity {

    // ── Identity ─────────────────────────────────────────────

    /**
     * Business document number — shared across all versions.
     * Format: DOC-{CATEGORY_PREFIX}-{YYYYMM}-{NNNN}
     * e.g. DOC-SOP-202404-0001
     */
    @Column(name = "doc_number", nullable = false, length = 40)
    private String docNumber;

    /**
     * Semantic version string.
     * Major versions (1.0, 2.0) = significant content changes.
     * Minor versions (1.1, 1.2) = minor corrections within same major.
     */
    @Column(name = "version", nullable = false, length = 10)
    private String version;

    /** Major version number — used to detect backward-incompatible changes. */
    @Column(name = "major_version", nullable = false)
    private Integer majorVersion;

    /** Minor version number. */
    @Column(name = "minor_version", nullable = false)
    private Integer minorVersion;

    // ── Classification ────────────────────────────────────────

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private DocumentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    @Builder.Default
    private AccessLevel accessLevel = AccessLevel.PUBLIC;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "tags", length = 500)
    private String tags;   // comma-separated keywords for search

    // ── Ownership ─────────────────────────────────────────────

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "owner_name", length = 150)
    private String ownerName;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "author_name", length = 150)
    private String authorName;

    // ── File metadata ─────────────────────────────────────────

    /** Original filename as uploaded by the user. */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /**
     * Opaque storage key — interpreted by the active StorageService.
     * Local: relative path from storage root.
     * S3: "bucket/prefix/uuid-filename.pdf"
     * Never expose this to the client — it reveals storage topology.
     */
    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;

    /** MIME type detected by Apache Tika (not trusted from the browser). */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** File size in bytes. */
    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    /**
     * SHA-256 hex digest of the raw file bytes.
     * Computed at upload time. Verified on every download to detect tampering.
     */
    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    // ── Dates ─────────────────────────────────────────────────

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "obsoleted_at")
    private LocalDateTime obsoletedAt;

    // ── Approval ─────────────────────────────────────────────

    @Column(name = "approved_by_id")
    private Long approvedById;

    @Column(name = "approved_by_name", length = 150)
    private String approvedByName;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_comments", length = 1000)
    private String approvalComments;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    // ── Versioning ────────────────────────────────────────────

    /**
     * Points to the immediately preceding version of this document.
     * Null for the first version.
     */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    /** Whether this document is subject to controlled distribution. */
    @Column(name = "is_controlled", nullable = false)
    @Builder.Default
    private Boolean isControlled = true;

    /** Download count — tracked for compliance reporting. */
    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private Long downloadCount = 0L;

    // ── One-to-many: approval history ─────────────────────────

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DocumentApproval> approvals = new ArrayList<>();

    // ── Convenience helpers ───────────────────────────────────

    public boolean isEffective()  { return status == DocumentStatus.EFFECTIVE; }
    public boolean isDraft()      { return status == DocumentStatus.DRAFT; }
    public boolean isTerminal()   {
        return status == DocumentStatus.OBSOLETE
            || status == DocumentStatus.WITHDRAWN
            || status == DocumentStatus.SUPERSEDED;
    }

    public boolean isExpired() {
        return expiryDate != null && LocalDate.now().isAfter(expiryDate);
    }

    public boolean isExpiringSoon(int warningDays) {
        return expiryDate != null
            && !isExpired()
            && !LocalDate.now().plusDays(warningDays).isBefore(expiryDate);
    }

    /** Human-readable full version string. */
    public String getFullVersion() {
        return majorVersion + "." + minorVersion;
    }

    public void incrementDownloadCount() {
        this.downloadCount = (this.downloadCount == null ? 0L : this.downloadCount) + 1;
    }
}
