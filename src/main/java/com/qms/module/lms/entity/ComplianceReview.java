package com.qms.module.lms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A reviewer's decision on a trainee's ComplianceSubmission.
 *
 * For SCHEDULED / SELF training: created by the Coordinator or Trainer.
 * For INDUCTION: created in three passes — Coordinator → HR → QA Head.
 *
 * Relationships:
 *   One ComplianceReview → One ComplianceSubmission
 */
@Entity
@Table(
    name = "lms_compliance_reviews",
    indexes = @Index(name = "idx_comprev_submission", columnList = "submission_id")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplianceReview {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, updatable = false, unique = true)
    private ComplianceSubmission submission;

    // ── Reviewer ──────────────────────────────────────────────

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewer_name", length = 150)
    private String reviewerName;

    /**
     * Role context of the reviewer: COORDINATOR, HR, QA_HEAD.
     * Useful for induction multi-step audit trail.
     */
    @Column(name = "reviewer_role", length = 50)
    private String reviewerRole;

    // ── Decision ──────────────────────────────────────────────

    /** APPROVED or REJECTED */
    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist private void onCreate() {
        createdAt  = LocalDateTime.now();
        reviewedAt = LocalDateTime.now();
    }
}
