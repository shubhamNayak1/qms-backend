package com.qms.module.qms.capa.dto.response;

import com.qms.module.qms.common.dto.response.QmsBaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CapaResponse extends QmsBaseResponse {

    private String    capaOrigin;
    private String    parentRecordType;
    private Long      parentRecordId;
    private String    parentRecordNumber;
    private String    source;
    private String    capaType;
    private String    preventiveAction;
    private Boolean   siteHeadRequired;
    private String    verificationReviewComment;
    private LocalDate effectivenessCheckDate;
    private String    effectivenessResult;
    private Boolean   isEffective;
    private String    assessmentFrequency;
    private Integer   assessmentCount;
    private String    assessmentSummaryStatus;
    private String    linkedDeviationNumber;
}
