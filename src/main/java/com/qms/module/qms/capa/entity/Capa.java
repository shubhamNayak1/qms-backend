package com.qms.module.qms.capa.entity;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.qms.common.entity.QmsRecord;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Corrective And Preventive Action record.
 *
 * Extends QmsRecord for all shared fields (status, priority, assignment,
 * dates, workflow history). Only CAPA-specific fields are defined here.
 */
@Entity
@Table(
    name = "qms_capa",
    indexes = {
        @Index(name = "idx_capa_status",   columnList = "status"),
        @Index(name = "idx_capa_priority", columnList = "priority"),
        @Index(name = "idx_capa_assigned", columnList = "assigned_to_id"),
        @Index(name = "idx_capa_number",   columnList = "record_number", unique = true),
        @Index(name = "idx_capa_due",      columnList = "due_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Capa extends QmsRecord {

    // ── Origin / polymorphic parent ───────────────────────────
    /** NEW or EXISTING — set at create time. */
    @Column(name = "capa_origin", length = 20)
    private String capaOrigin;

    /**
     * Polymorphic parent type: INCIDENT / DEVIATION / CHANGE_CONTROL /
     * MARKET_COMPLAINT. NULL when capaOrigin = NEW.
     */
    @Column(name = "parent_record_type", length = 30)
    private String parentRecordType;

    @Column(name = "parent_record_id")
    private Long parentRecordId;

    /** Denormalised parent record number for cross-link UI. */
    @Column(name = "parent_record_number", length = 30)
    private String parentRecordNumber;

    /** Origin of the CAPA — what triggered it. */
    @Column(name = "source", length = 100)
    private String source;  // Audit, Complaint, Deviation, Inspection, Internal

    /** CAPA type classification. */
    @Column(name = "capa_type", length = 80)
    private String capaType;  // Corrective / Preventive / Both

    /** Description of preventive measures taken. */
    @Column(name = "preventive_action", columnDefinition = "TEXT")
    private String preventiveAction;

    /**
     * Set at the 2nd PENDING_QA_REVIEW pass — drives the optional
     * PENDING_SITE_HEAD branch.
     */
    @Column(name = "site_head_required")
    private Boolean siteHeadRequired = false;

    /**
     * QA Reviewer narrative captured at PENDING_VERIFICATION_REVIEW —
     * accept or reject the dept HOD's verification.
     */
    @Column(name = "verification_review_comment", columnDefinition = "TEXT")
    private String verificationReviewComment;

    /** Date on which the effectiveness of the CAPA will be checked. */
    @Column(name = "effectiveness_check_date")
    private LocalDate effectivenessCheckDate;

    /** Result of the effectiveness verification. */
    @Column(name = "effectiveness_result", columnDefinition = "TEXT")
    private String effectivenessResult;

    /** Whether the CAPA was verified as effective. */
    @Column(name = "is_effective")
    private Boolean isEffective;

    // ── Effectiveness-assessment lifecycle ────────────────────
    /** MONTHLY / QUARTERLY / SEMI_ANNUAL / ANNUAL — set at CLOSED by Head QA. */
    @Column(name = "assessment_frequency", length = 20)
    private String assessmentFrequency;

    /** Number of scheduled effectiveness assessments. */
    @Column(name = "assessment_count")
    private Integer assessmentCount;

    /**
     * Denormalised summary of qms_capa_assessments status:
     * NOT_REQUIRED / IN_PROGRESS / COMPLETE.
     */
    @Column(name = "assessment_summary_status", length = 20)
    private String assessmentSummaryStatus = "NOT_REQUIRED";

    /**
     * Reference to a linked Deviation record (if this CAPA arose from one).
     *
     * <p><b>Legacy</b> — kept as a read-only view for rows created before
     * V24. New code should rely on {@link #parentRecordType} +
     * {@link #parentRecordNumber} for cross-module linking.</p>
     */
    @Column(name = "linked_deviation_number", length = 30)
    private String linkedDeviationNumber;

    @PrePersist
    private void prePersist() {
        setRecordType(QmsRecordType.CAPA);
    }
}
