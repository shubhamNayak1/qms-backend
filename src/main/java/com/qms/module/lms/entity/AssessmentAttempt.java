package com.qms.module.lms.entity;

import com.qms.module.lms.enums.AssessmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One attempt at an assessment by a user within an enrollment.
 *
 * A new row is created each time the user starts the assessment.
 * The attemptNumber increments per enrollment (not globally).
 *
 * Relationships:
 *   Many AssessmentAttempt → One Enrollment
 *   Many AssessmentAttempt → One Assessment
 */
@Entity
@Table(
    name = "lms_assessment_attempts",
    indexes = {
        @Index(name = "idx_attempt_enrollment",  columnList = "enrollment_id"),
        @Index(name = "idx_attempt_assessment",  columnList = "assessment_id"),
        @Index(name = "idx_attempt_status",      columnList = "status")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AssessmentAttempt {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, updatable = false)
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false, updatable = false)
    private Assessment assessment;

    /** 1-based attempt counter within this enrollment. */
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AssessmentStatus status = AssessmentStatus.NOT_STARTED;

    /** Raw score achieved (marks correct). */
    @Column(name = "raw_score")
    private Integer rawScore;

    /** Total possible marks for this attempt. */
    @Column(name = "total_marks")
    private Integer totalMarks;

    /** Percentage score (0–100). Computed after submission. */
    @Column(name = "score_percent")
    private Integer scorePercent;

    /** Whether the attempt met the pass mark. */
    @Column(name = "passed")
    private Boolean passed;

    /** JSON: map of questionId → userAnswer (for review/grading). */
    @Column(name = "answers_json", columnDefinition = "TEXT")
    private String answersJson;

    /** Reviewer's comments (used when SHORT_ANSWER questions are manually graded). */
    @Column(name = "reviewer_comments", length = 1000)
    private String reviewerComments;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist private void onCreate() { createdAt = LocalDateTime.now(); }
}
