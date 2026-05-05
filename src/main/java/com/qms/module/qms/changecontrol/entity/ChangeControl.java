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

    /** Product / material the change applies to — typed on the Create page. */
    @Column(name = "product_material", length = 255)
    private String productMaterial;

    /** Markets / regions impacted — typed on the Create page. */
    @Column(name = "market_details", columnDefinition = "TEXT")
    private String marketDetails;

    /**
     * Optional CAPA record number this change is linked to. Filled by the
     * HOD at PENDING_HOD review; the UI deep-links to the CAPA detail page
     * when the value matches a known record.
     */
    @Column(name = "linked_capa_number", length = 30)
    private String linkedCapaNumber;

    /** Low / Medium / High — set by QA Reviewer at QA_REVIEW stage. */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    // risk_assessment moved to QmsRecord parent (shared across all modules)

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

    // customer_comment moved to QmsRecord parent (shared with all modules).
    // customer_comment_required is kept here under its existing DB column name
    // for backwards compatibility with the existing request/response DTOs —
    // it routes the workflow through PENDING_CUSTOMER_COMMENT specifically for
    // Change Control. The parent's customer_communication_required is the
    // generic equivalent for other modules.
    @Column(name = "customer_comment_required")
    private Boolean customerCommentRequired = false;

    @PrePersist
    private void prePersist() { setRecordType(QmsRecordType.CHANGE_CONTROL); }
}
