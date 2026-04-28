package com.qms.module.lms.entity;

import com.qms.module.lms.enums.ComplianceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A trainee's compliance evidence submission after completing the training.
 *
 * Contains:
 *   - Attachment (proof of attendance / certificate / signed form)
 *   - Q&A answers (JSON array if the program has configured questions)
 *
 * After submission the status is PENDING until a Coordinator/HR/QA Head
 * creates a ComplianceReview.
 *
 * Relationships:
 *   One ComplianceSubmission → One Enrollment
 *   One ComplianceSubmission → One ComplianceReview (created on review)
 */
@Entity
@Table(
    name = "lms_compliance_submissions",
    indexes = {
        @Index(name = "idx_compsub_enrollment", columnList = "enrollment_id"),
        @Index(name = "idx_compsub_status",     columnList = "status")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplianceSubmission {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, updatable = false, unique = true)
    private Enrollment enrollment;

    // ── Attachment ────────────────────────────────────────────

    /** Storage key / path of the uploaded attachment (PDF, image, etc.). */
    @Column(name = "attachment_storage_key", length = 500)
    private String attachmentStorageKey;

    @Column(name = "attachment_file_name", length = 255)
    private String attachmentFileName;

    @Column(name = "attachment_file_size_bytes")
    private Long attachmentFileSizeBytes;

    // ── Q&A ───────────────────────────────────────────────────

    /**
     * JSON array of question-answer pairs, e.g.:
     * [{"question":"What is GMP?","answer":"Good Manufacturing Practice"}]
     */
    @Column(name = "qna_answers", columnDefinition = "TEXT")
    private String qnaAnswers;

    // ── Status ────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ComplianceStatus status = ComplianceStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ── Timestamps ────────────────────────────────────────────

    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Review ────────────────────────────────────────────────

    @OneToOne(mappedBy = "submission", cascade = CascadeType.ALL,
              orphanRemoval = true, fetch = FetchType.LAZY)
    private ComplianceReview review;

    @PrePersist  private void onCreate() { submittedAt = LocalDateTime.now(); }
    @PreUpdate   private void onUpdate() { updatedAt   = LocalDateTime.now(); }
}
