package com.qms.module.lms.entity;

import com.qms.common.base.BaseEntity;
import com.qms.module.lms.enums.EnrollmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An Enrollment represents one user's assignment to one TrainingProgram.
 *
 * This is the central "progress tracking" record of the LMS.
 *
 * Relationships:
 *   Many Enrollments → One TrainingProgram
 *   One Enrollment   → Many ContentProgress (one per ProgramContent item)
 *   One Enrollment   → One AssessmentAttempt (most recent attempt)
 *   One Enrollment   → One TrainingCertificate (issued on COMPLETED)
 *
 * The user reference is a plain Long (no FK) so enrollments survive
 * the user module's own lifecycle changes.
 *
 * Unique constraint: one enrollment per user per program — duplication
 * is rejected at the DB level. Re-training after EXPIRED creates a new
 * enrollment (the old one is archived, not overwritten).
 */
@Entity
@Table(
    name = "lms_enrollments",
    indexes = {
        @Index(name = "idx_enroll_user",       columnList = "user_id"),
        @Index(name = "idx_enroll_program",    columnList = "program_id"),
        @Index(name = "idx_enroll_status",     columnList = "status"),
        @Index(name = "idx_enroll_deadline",   columnList = "due_date"),
        @Index(name = "idx_enroll_deleted",    columnList = "is_deleted")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name  = "uk_enrollment_user_program",
            columnNames = {"user_id", "program_id"}
        )
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Enrollment extends BaseEntity {

    // ── Who ─────────────────────────────────────────────────

    /** Ref to users.id — plain BIGINT, no FK by design. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "user_name", length = 150)
    private String userName;

    @Column(name = "user_email", length = 200)
    private String userEmail;

    @Column(name = "user_department", length = 100)
    private String userDepartment;

    // ── What ────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false)
    private TrainingProgram program;

    // ── Status ──────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.ENROLLED;

    // ── Dates ───────────────────────────────────────────────

    /** Computed from assignment date + program.completionDeadlineDays. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ── Progress ────────────────────────────────────────────

    /** Percentage of required content items completed (0–100). */
    @Column(name = "progress_percent", nullable = false)
    @Builder.Default
    private Integer progressPercent = 0;

    /** How many assessment attempts the user has consumed. */
    @Column(name = "attempts_used", nullable = false)
    @Builder.Default
    private Integer attemptsUsed = 0;

    /** Score on the most recent assessment attempt (0–100). */
    @Column(name = "last_score")
    private Integer lastScore;

    // ── Assignment metadata ─────────────────────────────────

    @Column(name = "assigned_by_id")
    private Long assignedById;

    @Column(name = "assigned_by_name", length = 150)
    private String assignedByName;

    /** Manager-supplied reason for assigning this training. */
    @Column(name = "assignment_reason", length = 500)
    private String assignmentReason;

    // ── Waiver (if status = WAIVED) ─────────────────────────

    @Column(name = "waiver_reason", length = 500)
    private String waiverReason;

    @Column(name = "waived_by_name", length = 150)
    private String waivedByName;

    @Column(name = "waived_at")
    private LocalDateTime waivedAt;

    // ── Relationships ───────────────────────────────────────

    @OneToMany(mappedBy = "enrollment", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ContentProgress> contentProgressList = new ArrayList<>();

    @OneToMany(mappedBy = "enrollment", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AssessmentAttempt> assessmentAttempts = new ArrayList<>();

    @OneToOne(mappedBy = "enrollment", cascade = CascadeType.ALL,
              orphanRemoval = true, fetch = FetchType.LAZY)
    private TrainingCertificate certificate;

    // ── Helpers ─────────────────────────────────────────────

    public boolean isOverdue() {
        return dueDate != null
            && LocalDate.now().isAfter(dueDate)
            && status != EnrollmentStatus.COMPLETED
            && status != EnrollmentStatus.WAIVED
            && status != EnrollmentStatus.CANCELLED;
    }

    public boolean isCompliant() {
        return status == EnrollmentStatus.COMPLETED || status == EnrollmentStatus.WAIVED;
    }
}
