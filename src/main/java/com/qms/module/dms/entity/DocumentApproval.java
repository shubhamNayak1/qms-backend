package com.qms.module.dms.entity;

import com.qms.module.dms.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Individual approver action on a document.
 *
 * Supports multi-approver workflows:
 *   - Each approver gets one row per review cycle.
 *   - DocumentWorkflowService checks how many APPROVED rows exist
 *     to decide if the quorum (dms.workflow.required-approvers) is met.
 *   - Immutable after creation — status changes add new rows, never update old ones.
 */
@Entity
@Table(
    name = "dms_document_approvals",
    indexes = {
        @Index(name = "idx_approval_doc",      columnList = "document_id"),
        @Index(name = "idx_approval_approver", columnList = "approver_id"),
        @Index(name = "idx_approval_status",   columnList = "decision")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private Document document;

    @Column(name = "approver_id", nullable = false, updatable = false)
    private Long approverId;

    @Column(name = "approver_name", nullable = false, length = 150, updatable = false)
    private String approverName;

    @Column(name = "approver_role", length = 80, updatable = false)
    private String approverRole;

    /**
     * Decision made: APPROVED or REJECTED (re-uses DocumentStatus for clarity).
     * UNDER_REVIEW = pending, not yet acted.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus decision = DocumentStatus.UNDER_REVIEW;

    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Which review cycle this belongs to (increments after each rejection + resubmit). */
    @Column(name = "review_cycle", nullable = false)
    @Builder.Default
    private Integer reviewCycle = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
