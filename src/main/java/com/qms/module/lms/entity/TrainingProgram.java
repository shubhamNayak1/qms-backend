package com.qms.module.lms.entity;

import com.qms.common.base.BaseEntity;
import com.qms.module.lms.enums.ProgramStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A Training Program is the top-level course definition.
 *
 * Relationships:
 *   ── One Program has many ProgramContent items (ordered content list)
 *   ── One Program has many ProgramDocumentLinks (DMS links)
 *   ── One Program has many Enrollments (user assignments)
 *   ── One Program optionally has one Assessment
 *
 * A program is a TEMPLATE. When a user is assigned, an Enrollment record
 * is created. Progress is tracked on the Enrollment, not the Program.
 *
 * Role Separation:
 *   TRAINING_MANAGER creates/edits Programs.
 *   MANAGER assigns Programs to users (creates Enrollments).
 *   Users consume content via their Enrollments.
 */
@Entity
@Table(
    name = "lms_programs",
    indexes = {
        @Index(name = "idx_prog_status",     columnList = "status"),
        @Index(name = "idx_prog_category",   columnList = "category"),
        @Index(name = "idx_prog_department", columnList = "department"),
        @Index(name = "idx_prog_deleted",    columnList = "is_deleted")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrainingProgram extends BaseEntity {

    // ── Identity & classification ────────────────────────────

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;   // e.g. GMP-001, SOP-GOWN-01

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 100)
    private String category;  // e.g. GMP, Safety, Quality, Technical

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "tags", length = 500)
    private String tags;  // comma-separated keywords

    // ── Status & visibility ─────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProgramStatus status = ProgramStatus.DRAFT;

    @Column(name = "is_mandatory", nullable = false)
    @Builder.Default
    private Boolean isMandatory = false;

    // ── Training config ─────────────────────────────────────

    /** Estimated time to complete in minutes. */
    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    /** Certificate validity in years after completion. Null = no certificate. */
    @Column(name = "certificate_validity_years")
    private Integer certificateValidityYears;

    /** Number of days within which a new enrollment must be completed. */
    @Column(name = "completion_deadline_days")
    private Integer completionDeadlineDays;

    /** Whether the assessment must be passed to mark the enrollment COMPLETED. */
    @Column(name = "assessment_required", nullable = false)
    @Builder.Default
    private Boolean assessmentRequired = false;

    /** Pass mark percentage (0–100). Defaults to global lms.assessment.min-pass-score. */
    @Column(name = "pass_score")
    @Builder.Default
    private Integer passScore = 80;

    /** Maximum assessment re-attempts before a manager must reset. */
    @Column(name = "max_attempts")
    @Builder.Default
    private Integer maxAttempts = 3;

    // ── Ownership ───────────────────────────────────────────

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "owner_name", length = 150)
    private String ownerName;

    // ── Relationships ───────────────────────────────────────

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<ProgramContent> contents = new ArrayList<>();

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProgramDocumentLink> documentLinks = new ArrayList<>();

    @OneToMany(mappedBy = "program", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Enrollment> enrollments = new ArrayList<>();

    @OneToOne(mappedBy = "program", cascade = CascadeType.ALL,
              orphanRemoval = true, fetch = FetchType.LAZY)
    private Assessment assessment;

    // ── Helpers ─────────────────────────────────────────────

    public boolean isPublishable() {
        return !contents.isEmpty() && status == ProgramStatus.DRAFT;
    }
}
