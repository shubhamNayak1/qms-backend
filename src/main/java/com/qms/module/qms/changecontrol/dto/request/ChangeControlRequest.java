package com.qms.module.qms.changecontrol.dto.request;

import com.qms.module.qms.common.dto.request.QmsBaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Request body for creating or updating a Change Control record")
public class ChangeControlRequest extends QmsBaseRequest {

    @Schema(example = "Process", description = "Process / Equipment / Document / System / Supplier / Facility")
    private String changeType;

    private String changeReason;

    @Schema(description = "Product / material the change applies to (Initiator-supplied at Create)")
    private String productMaterial;

    @Schema(description = "Markets / regions impacted by the change (Initiator-supplied at Create)")
    private String marketDetails;

    @Schema(description = "Optional CAPA record number this change is linked to (filled at PENDING_HOD)")
    private String linkedCapaNumber;

    @Schema(example = "Medium", description = "Low / Medium / High — set at QA_REVIEW stage")
    private String riskLevel;

    private String    riskAssessment;
    private String    implementationPlan;
    private LocalDate implementationDate;
    private Boolean   validationRequired;
    private String    validationDetails;
    private LocalDate validationCompletionDate;
    private Boolean   regulatorySubmissionRequired;
    private String    regulatorySubmissionReference;
    private String    rollbackPlan;

    @Schema(description = "Whether Site Head approval is required — routes through PENDING_SITE_HEAD step")
    private Boolean siteHeadRequired;

    @Schema(description = "Whether customer comment is required — routes through PENDING_CUSTOMER_COMMENT step")
    private Boolean customerCommentRequired;

    @Schema(description = "Customer comment text — filled during PENDING_CUSTOMER_COMMENT stage")
    private String customerComment;
}
