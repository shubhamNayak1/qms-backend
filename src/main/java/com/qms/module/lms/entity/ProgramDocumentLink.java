package com.qms.module.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Links a TrainingProgram to one or more DMS documents as reference material.
 *
 * Difference from ProgramContent (DOCUMENT type):
 *   ProgramContent.DOCUMENT — the document IS the training content (must be read).
 *   ProgramDocumentLink       — the document is reference material (supplementary).
 *
 * This table lets the LMS compliance dashboard flag a training program as
 * outdated when its linked DMS document is revised to a new version.
 *
 * Relationships:
 *   Many ProgramDocumentLink → One TrainingProgram
 */
@Entity
@Table(
    name = "lms_program_document_links",
    indexes = {
        @Index(name = "idx_pdl_program",  columnList = "program_id"),
        @Index(name = "idx_pdl_doc_id",   columnList = "dms_document_id"),
        @Index(name = "idx_pdl_doc_num",  columnList = "dms_doc_number")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProgramDocumentLink {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false)
    private TrainingProgram program;

    // ── DMS reference ───────────────────────────────────────

    /** dms_documents.id of the linked document. */
    @Column(name = "dms_document_id", nullable = false)
    private Long dmsDocumentId;

    /** Document number e.g. DOC-SOP-202404-0001 (denormalized for fast lookup). */
    @Column(name = "dms_doc_number", nullable = false, length = 40)
    private String dmsDocNumber;

    /** Version of the document at the time of linking, e.g. "1.0". */
    @Column(name = "dms_doc_version", nullable = false, length = 10)
    private String dmsDocVersion;

    /** Document title at the time of linking (denormalized for display). */
    @Column(name = "dms_doc_title", length = 300)
    private String dmsDocTitle;

    /**
     * Whether the training program should be flagged for review when
     * the DMS document is updated to a new version.
     */
    @Column(name = "trigger_review_on_update", nullable = false)
    @Builder.Default
    private Boolean triggerReviewOnUpdate = true;

    @Column(name = "linked_by", length = 100)
    private String linkedBy;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    @PrePersist private void onCreate() { linkedAt = LocalDateTime.now(); }
}
