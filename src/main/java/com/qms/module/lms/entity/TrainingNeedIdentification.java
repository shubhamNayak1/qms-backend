package com.qms.module.lms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Training Need Identification (TNI) — auto-generated after an INDUCTION
 * training is fully approved (Coordinator → HR → QA Head).
 *
 * Captures the training gaps identified for a new employee based on their
 * Job Description (JD), and lists recommended future programs.
 *
 * Relationships:
 *   One TNI → One Enrollment (the completed induction enrollment)
 */
@Entity
@Table(
    name = "lms_training_needs",
    indexes = {
        @Index(name = "idx_tni_enrollment", columnList = "enrollment_id"),
        @Index(name = "idx_tni_user",       columnList = "user_id")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrainingNeedIdentification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    // ── Linked enrollment ─────────────────────────────────────

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    // ── Employee snapshot ─────────────────────────────────────

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", length = 150)
    private String userName;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "designation", length = 150)
    private String designation;

    // ── TNI content ───────────────────────────────────────────

    /** Summary of the employee's Job Description used to identify gaps. */
    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    /** Free-text or structured list of identified training gaps. */
    @Column(name = "identified_gaps", columnDefinition = "TEXT")
    private String identifiedGaps;

    /**
     * JSON array of recommended program codes / titles, e.g.:
     * [{"code":"GMP-001","title":"GMP Awareness"},{"code":"SOP-001","title":"SOP Training"}]
     */
    @Column(name = "recommended_trainings", columnDefinition = "TEXT")
    private String recommendedTrainings;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ── Audit ─────────────────────────────────────────────────

    @Column(name = "generated_by", length = 150)
    private String generatedBy;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist  private void onCreate() { generatedAt = LocalDateTime.now(); }
    @PreUpdate   private void onUpdate() { updatedAt   = LocalDateTime.now(); }
}
