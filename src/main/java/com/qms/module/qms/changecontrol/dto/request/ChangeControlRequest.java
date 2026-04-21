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

    @Schema(example = "Medium", description = "Low / Medium / High")
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
}
