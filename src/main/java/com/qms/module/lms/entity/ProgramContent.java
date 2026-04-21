package com.qms.module.lms.entity;

import com.qms.module.lms.enums.ContentType;
import jakarta.persistence.*;
import lombok.*;

/**
 * A single learning content item within a TrainingProgram.
 * Items are ordered by displayOrder (ASC) to form the curriculum.
 *
 * DOCUMENT type items are linked to DMS via dmsDocumentId + dmsDocNumber.
 * The actual file is retrieved from the DMS module — the LMS only stores the reference.
 *
 * Relationships:
 *   Many ProgramContent → One TrainingProgram
 *   One ProgramContent → Many ContentProgress (one per enrolled user)
 */
@Entity
@Table(
    name = "lms_program_contents",
    indexes = {
        @Index(name = "idx_content_program", columnList = "program_id"),
        @Index(name = "idx_content_type",    columnList = "content_type"),
        @Index(name = "idx_content_dms",     columnList = "dms_document_id")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProgramContent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false)
    private TrainingProgram program;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 1-based ordering within the program. */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 1;

    /** Whether the user must complete this item before advancing to the next. */
    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean isRequired = true;

    /** Estimated time to consume this content in minutes. */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    // ── Content source ──────────────────────────────────────

    /** For DOCUMENT type: FK-style reference to dms_documents.id */
    @Column(name = "dms_document_id")
    private Long dmsDocumentId;

    /** For DOCUMENT type: human-readable doc number e.g. DOC-SOP-202404-0001 */
    @Column(name = "dms_doc_number", length = 40)
    private String dmsDocNumber;

    /** For DOCUMENT type: version at the time of linking e.g. "2.0" */
    @Column(name = "dms_doc_version", length = 10)
    private String dmsDocVersion;

    /** For VIDEO / EXTERNAL_LINK / SCORM: the URL or CDN path */
    @Column(name = "content_url", length = 1000)
    private String contentUrl;

    /** For TEXT type: inline HTML content stored here */
    @Column(name = "inline_content", columnDefinition = "LONGTEXT")
    private String inlineContent;

    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @PrePersist  private void onCreate()  { createdAt = java.time.LocalDateTime.now(); }
    @PreUpdate   private void onUpdate()  { updatedAt = java.time.LocalDateTime.now(); }
}
