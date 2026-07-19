package com.qms.module.qms.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Atomic action item attached to a {@link QmsDepartmentComment}.
 *
 * Round-N (2026-07-04) tester CC-Point-2 · Issue 6. Each dept-comment
 * row can now carry many action items, each with its own target date +
 * status + completed-by stamp. The scheduler (added in a follow-up)
 * will notify the dept HOD when items are approaching their target.
 */
@Entity
@Table(name = "qms_department_action_items", indexes = {
        @Index(name = "idx_qms_dai_dept_comment", columnList = "dept_comment_id"),
        @Index(name = "idx_qms_dai_target_date",  columnList = "target_date"),
        @Index(name = "idx_qms_dai_status",       columnList = "status"),
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QmsDepartmentActionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dept_comment_id", nullable = false)
    private Long deptCommentId;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    /** PENDING | IN_PROGRESS | COMPLETED */
    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by_id")
    private Long completedById;

    @Column(name = "completed_by_name", length = 160)
    private String completedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    // ── Batch C RED-5 (2026-07-19) ───────────────────────────

    /**
     * Optional dept-declared extension of {@link #targetDate}. When the
     * action item is overdue (targetDate &lt; today) the dept HOD must
     * fill this before they can upload a related attachment. The
     * extension is dept-side self-declared — no separate Head-QA
     * approval workflow.
     */
    @Column(name = "extension_date")
    private LocalDate extensionDate;

    @Column(name = "extension_reason", columnDefinition = "TEXT")
    private String extensionReason;

    @PrePersist
    private void onPersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (isDeleted == null) isDeleted = false;
    }

    @PreUpdate
    private void onUpdate() { updatedAt = LocalDateTime.now(); }
}
