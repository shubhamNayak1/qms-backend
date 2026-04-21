package com.qms.module.qms.deviation.dto.response;

import com.qms.module.qms.common.dto.response.QmsBaseResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviationResponse extends QmsBaseResponse {
    private String  deviationType;
    private String  productBatch;
    private String  processArea;
    private String  impactAssessment;
    private Boolean capaRequired;
    private String  capaReference;
    private Boolean regulatoryReportable;
}
