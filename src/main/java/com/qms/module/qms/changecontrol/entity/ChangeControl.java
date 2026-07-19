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

    /** Product / Material Name typed on the Create page. */
    @Column(name = "product_material", length = 255)
    private String productMaterial;

    /**
     * Product / Material Code typed on the Create page — split from
     * {@link #productMaterial} per the May-2026 tester feedback so the
     * material code becomes a discrete column reports can filter on.
     */
    @Column(name = "product_material_code", length = 100)
    private String productMaterialCode;

    /** Markets / regions impacted — typed on the Create page. */
    @Column(name = "market_details", columnDefinition = "TEXT")
    private String marketDetails;

    /**
     * QA's pre-dept-comment narrative captured at QA Evaluation Phase 1.
     * Visible to every invited dept HOD while they fill their per-dept
     * comments — gives them a single QA context paragraph instead of
     * forcing them to hunt through prior status history.
     */
    @Column(name = "pre_remark", columnDefinition = "TEXT")
    private String preRemark;

    /**
     * DMS document id (or free text) captured at Create time as the
     * Initiator's supporting attachment. Resolves to the DMS title /
     * version on the response — same parse-as-numeric-id pattern as
     * the dept-attachments table.
     */
    @Column(name = "initial_attachment_ref", length = 255)
    private String initialAttachmentRef;

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
    @Builder.Default
    private Boolean validationRequired = false;

    @Column(name = "validation_details", columnDefinition = "TEXT")
    private String validationDetails;

    @Column(name = "validation_completion_date")
    private LocalDate validationCompletionDate;

    /** Regulatory submission required for this change? */
    @Column(name = "regulatory_submission_required")
    @Builder.Default
    private Boolean regulatorySubmissionRequired = false;

    @Column(name = "regulatory_submission_reference", length = 100)
    private String regulatorySubmissionReference;

    /**
     * Batch B S3 (2026-07-19): free-text country / territory of the
     * regulatory submission. Captured by RA at PENDING_RA_REVIEW alongside
     * the Dossier reference. Optional — some submissions are multi-region
     * and RA leaves this blank.
     */
    @Column(name = "regulatory_submission_country", columnDefinition = "TEXT")
    private String regulatorySubmissionCountry;

    /**
     * Batch B S4 (2026-07-19): QA's summary verdict at Phase 2, captured
     * alongside the Post-Remark. The tester's reference doc lists this as
     * a distinct field ("QA Evaluation Remark") and the old code was
     * conflating it with Post-Remark.
     */
    @Column(name = "qa_evaluation_remark", columnDefinition = "TEXT")
    private String qaEvaluationRemark;

    @Column(name = "rollback_plan", columnDefinition = "TEXT")
    private String rollbackPlan;

    /**
     * Whether this change requires Site Head approval (routes through PENDING_SITE_HEAD).
     */
    @Column(name = "site_head_required")
    @Builder.Default
    private Boolean siteHeadRequired = false;

    // customer_comment moved to QmsRecord parent (shared with all modules).
    // customer_comment_required is kept here under its existing DB column name
    // for backwards compatibility with the existing request/response DTOs —
    // it routes the workflow through PENDING_CUSTOMER_COMMENT specifically for
    // Change Control. The parent's customer_communication_required is the
    // generic equivalent for other modules.
    @Column(name = "customer_comment_required")
    @Builder.Default
    private Boolean customerCommentRequired = false;

    // ── Round-N (2026-07-04) tester CC-Point-2 · Issue 1 ───────────
    // The HOD Assessment form gains a 7-checkbox "Impact" panel so
    // the HOD can flag what areas of the business are affected.
    // Each column defaults to FALSE via the V29 migration; the entity
    // marks them non-nullable via @Builder.Default to match.

    @Column(name = "impact_on_qualification")
    @Builder.Default
    private Boolean impactOnQualification = false;

    @Column(name = "impact_on_documentation")
    @Builder.Default
    private Boolean impactOnDocumentation = false;

    @Column(name = "impact_on_validation")
    @Builder.Default
    private Boolean impactOnValidation = false;

    @Column(name = "impact_on_material_source")
    @Builder.Default
    private Boolean impactOnMaterialSource = false;

    @Column(name = "impact_regulatory_aspects")
    @Builder.Default
    private Boolean impactRegulatoryAspects = false;

    @Column(name = "impact_on_artwork_pack")
    @Builder.Default
    private Boolean impactOnArtworkPack = false;

    @Column(name = "impact_other")
    @Builder.Default
    private Boolean impactOther = false;

    /** Populated only when {@link #impactOther} is TRUE. */
    @Column(name = "impact_other_comment", columnDefinition = "TEXT")
    private String impactOtherComment;

    // ── Round-N tester CC-Point-2 · Issue 2 ────────────────────────
    // Initial Risk Assessment toggle + conditional narrative. Same
    // pattern as regulatory_submission_required + reference: boolean
    // gates whether the narrative field is required.

    @Column(name = "initial_risk_assessment_required")
    @Builder.Default
    private Boolean initialRiskAssessmentRequired = false;

    @Column(name = "initial_risk_assessment", columnDefinition = "TEXT")
    private String initialRiskAssessment;

    @PrePersist
    private void prePersist() { setRecordType(QmsRecordType.CHANGE_CONTROL); }
}
