package com.qms.module.lms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks one user's progress on one ProgramContent item.
 *
 * One row exists per (enrollment, programContent) pair.
 * Created lazily when the user first opens the content item.
 *
 * Relationships:
 *   Many ContentProgress → One Enrollment
 *   Many ContentProgress → One ProgramContent
 */
@Entity
@Table(
    name = "lms_content_progress",
    indexes = {
        @Index(name = "idx_cp_enrollment", columnList = "enrollment_id"),
        @Index(name = "idx_cp_content",    columnList = "content_id"),
        @Index(name = "idx_cp_completed",  columnList = "completed_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name        = "uk_cp_enrollment_content",
            columnNames = {"enrollment_id", "content_id"}
        )
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContentProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, updatable = false)
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false, updatable = false)
    private ProgramContent content;

    /** How far into a video the user has watched (0–100 %). */
    @Column(name = "view_percent", nullable = false)
    @Builder.Default
    private Integer viewPercent = 0;

    /** True when the user has fully consumed the item (watched, read, acknowledged). */
    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;

    /** For DOCUMENT items: the user clicked the "I have read this document" button. */
    @Column(name = "acknowledged", nullable = false)
    @Builder.Default
    private Boolean acknowledged = false;

    @Column(name = "first_accessed_at")
    private LocalDateTime firstAccessedAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Total seconds the user has actively spent on this content item. */
    @Column(name = "time_spent_seconds", nullable = false)
    @Builder.Default
    private Long timeSpentSeconds = 0L;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() { createdAt = LocalDateTime.now(); }

    public void markCompleted() {
        this.isCompleted  = true;
        this.viewPercent  = 100;
        this.acknowledged = true;
        this.completedAt  = LocalDateTime.now();
    }
}
