package com.qms.module.qms.changecontrol.dto.response;

import com.qms.module.qms.common.dto.response.QmsBaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ChangeControlResponse extends QmsBaseResponse {
    private String    changeType;
    private String    changeReason;
    private String    productMaterial;
    private String    productMaterialCode;
    private String    marketDetails;
    private String    preRemark;
    private String    initialAttachmentRef;
    // DMS resolution for initialAttachmentRef when it parses as a doc id.
    private Long      initialAttachmentDmsId;
    private String    initialAttachmentDmsNumber;
    private String    initialAttachmentDmsTitle;
    private String    initialAttachmentDmsVersion;
    private String    linkedCapaNumber;
    private String    riskLevel;
    // initialAssessment + riskAssessment are inherited from QmsBaseResponse
    // (Round-2 F1: HOD's narrative and QA's narrative are separate fields).
    private String    implementationPlan;
    private LocalDate implementationDate;
    private Boolean   validationRequired;
    private String    validationDetails;
    private LocalDate validationCompletionDate;
    private Boolean   regulatorySubmissionRequired;
    private String    regulatorySubmissionReference;
    /** Batch B S3 — free-text country / territory of the RA submission. */
    private String    regulatorySubmissionCountry;
    /** Batch B S4 — QA's summary verdict at Phase 2, distinct from Post-Remark. */
    private String    qaEvaluationRemark;
    private String    rollbackPlan;
    private Boolean siteHeadRequired;
    private Boolean customerCommentRequired;
    private String  customerComment;

    // Round-N (2026-07-04) tester CC-Point-2 · Issues 1 + 2.
    private Boolean impactOnQualification;
    private Boolean impactOnDocumentation;
    private Boolean impactOnValidation;
    private Boolean impactOnMaterialSource;
    private Boolean impactRegulatoryAspects;
    private Boolean impactOnArtworkPack;
    private Boolean impactOther;
    private String  impactOtherComment;
    private Boolean initialRiskAssessmentRequired;
    private String  initialRiskAssessment;
}
