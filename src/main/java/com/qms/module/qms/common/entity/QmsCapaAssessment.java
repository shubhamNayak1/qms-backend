package com.qms.module.qms.common.entity;

import com.qms.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * QmsCapaAssessment — one row per scheduled CAPA effectiveness-assessment
 * cycle. Created in bulk at CAPA closure time (count = capa.assessment_count,
 * spaced by capa.assessment_frequency starting from the closure date).
 *
 * Lifecycle of a single row:
 *   PENDING   — scheduled, waiting for the responsible dept to fill
 *   SUBMITTED — dept has filled action_observed + evidence; awaiting QA review
 *   ACCEPTED  — QA Reviewer accepted this cycle
 *   REJECTED  — QA Reviewer rejected; dept must re-submit
 *
 * The parent CAPA's status moves between EFFECTIVENESS_PENDING (any rows
 * still PENDING) and EFFECTIVENESS_REVIEW (any rows SUBMITTED awaiting QA),
 * and ultimately reaches EFFECTIVENESS_VERIFIED when every row is ACCEPTED.
 */
@Entity
@Table(
    name = "qms_capa_assessments",
    indexes = {
        @Index(name = "idx_qca_capa",        columnList = "capa_id"),
        @Index(name = "idx_qca_capa_status", columnList = "capa_id,status"),
        @Index(name = "idx_qca_due",         columnList = "due_date,status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QmsCapaAssessment extends BaseEntity {

    @Column(name = "capa_id", nullable = false)
    private Long capaId;

    /** 1-based sequence number — matches qms_capa.assessment_count. */
    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    /** Scheduled date for this cycle (set at closure based on frequency). */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** PENDING / SUBMITTED / ACCEPTED / REJECTED. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "action_observed", columnDefinition = "TEXT")
    private String actionObserved;

    @Column(name = "evidence_ref", length = 255)
    private String evidenceRef;

    /** Dept's verdict on this cycle. */
    @Column(name = "is_effective")
    private Boolean isEffective;

    @Column(name = "completed_by_id")
    private Long completedById;

    @Column(name = "completed_by_name", length = 150)
    private String completedByName;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** ACCEPTED / REJECTED — set by QA Reviewer at EFFECTIVENESS_REVIEW. */
    @Column(name = "review_status", length = 20)
    private String reviewStatus;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    @Column(name = "reviewed_by_name", length = 150)
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
