package com.qms.module.qms.changecontrol.entity;

import com.qms.common.enums.QmsRecordType;
import com.qms.module.qms.common.entity.QmsRecord;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "qms_change_control", indexes = {
        @Index(name = "idx_cc_status",   columnList = "status"),
        @Index(name = "idx_cc_priority", columnList = "priority"),
        @Index(name = "idx_cc_number",   columnList = "record_number", unique = true),
        @Index(name = "idx_cc_type",     columnList = "change_type")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChangeControl extends QmsRecord {

    /** Process / Equipment / Document / System / Supplier / Facility */
    @Column(name = "change_type", length = 80)
    private String changeType;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    /** Low / Medium / High */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "risk_assessment", columnDefinition = "TEXT")
    private String riskAssessment;

    @Column(name = "implementation_plan", columnDefinition = "TEXT")
    private String implementationPlan;

    @Column(name = "implementation_date")
    private LocalDate implementationDate;

    @Column(name = "validation_required")
    private Boolean validationRequired = false;

    @Column(name = "validation_details", columnDefinition = "TEXT")
    private String validationDetails;

    @Column(name = "validation_completion_date")
    private LocalDate validationCompletionDate;

    /** Regulatory submission required for this change? */
    @Column(name = "regulatory_submission_required")
    private Boolean regulatorySubmissionRequired = false;

    @Column(name = "regulatory_submission_reference", length = 100)
    private String regulatorySubmissionReference;

    @Column(name = "rollback_plan", columnDefinition = "TEXT")
    private String rollbackPlan;

    /**
     * Whether this change requires Site Head approval (routes through PENDING_SITE_HEAD).
     */
    @Column(name = "site_head_required")
    private Boolean siteHeadRequired = false;

    /**
     * Whether customer notification/comment is required (routes through PENDING_CUSTOMER_COMMENT).
     */
    @Column(name = "customer_comment_required")
    private Boolean customerCommentRequired = false;

    /** Customer comment text (filled in during PENDING_CUSTOMER_COMMENT stage). */
    @Column(name = "customer_comment", columnDefinition = "TEXT")
    private String customerComment;

    @PrePersist
    private void prePersist() { setRecordType(QmsRecordType.CHANGE_CONTROL); }
}
