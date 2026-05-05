package com.qms.module.qms.capa.dto.request;

import com.qms.module.qms.common.dto.request.QmsBaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Request body for creating or updating a CAPA record")
public class CapaRequest extends QmsBaseRequest {

    @Schema(example = "NEW", description = "NEW or EXISTING (existing = raised against an Incident / Deviation / CC / MC)")
    private String capaOrigin;

    @Schema(description = "Parent record type when capaOrigin = EXISTING: INCIDENT / DEVIATION / CHANGE_CONTROL / MARKET_COMPLAINT")
    private String parentRecordType;

    @Schema(description = "Parent record id when capaOrigin = EXISTING")
    private Long parentRecordId;

    @Schema(description = "Parent record number (denormalised for cross-link UI)")
    private String parentRecordNumber;

    @Schema(example = "Audit", description = "What triggered this CAPA: Audit, Complaint, Deviation, Inspection, Internal")
    private String source;

    @Schema(example = "Corrective", description = "CAPA type: Corrective / Preventive / Both")
    private String capaType;

    @Schema(description = "Description of preventive measures to be taken")
    private String preventiveAction;

    @Schema(description = "Set at the 2nd QA Review pass — routes through PENDING_SITE_HEAD when true")
    private Boolean siteHeadRequired;

    @Schema(description = "QA Reviewer narrative captured at PENDING_VERIFICATION_REVIEW")
    private String verificationReviewComment;

    @Schema(description = "Date on which effectiveness will be verified")
    private LocalDate effectivenessCheckDate;

    @Schema(description = "MONTHLY / QUARTERLY / SEMI_ANNUAL / ANNUAL — set at CLOSED by Head QA")
    private String assessmentFrequency;

    @Schema(description = "Number of scheduled effectiveness assessments")
    private Integer assessmentCount;

    @Schema(description = "Reference to a linked deviation record, e.g. DEV-202404-0003 (legacy — prefer parentRecordType/Number)")
    private String linkedDeviationNumber;
}
