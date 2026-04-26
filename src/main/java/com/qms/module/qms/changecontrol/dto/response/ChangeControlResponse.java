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
    private String    riskLevel;
    private String    riskAssessment;
    private String    implementationPlan;
    private LocalDate implementationDate;
    private Boolean   validationRequired;
    private String    validationDetails;
    private LocalDate validationCompletionDate;
    private Boolean   regulatorySubmissionRequired;
    private String    regulatorySubmissionReference;
    private String    rollbackPlan;
    private Boolean siteHeadRequired;
    private Boolean customerCommentRequired;
    private String  customerComment;
}
