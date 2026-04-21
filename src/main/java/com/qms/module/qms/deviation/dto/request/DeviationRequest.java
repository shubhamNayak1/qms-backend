package com.qms.module.qms.deviation.dto.request;

import com.qms.module.qms.common.dto.request.QmsBaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request body for creating or updating a Deviation record")
public class DeviationRequest extends QmsBaseRequest {

    @Schema(example = "Unplanned", description = "Planned or Unplanned")
    private String deviationType;

    @Schema(example = "BATCH-2024-001", description = "Affected product batch/lot number")
    private String productBatch;

    @Schema(example = "Filling Line 3")
    private String processArea;

    private String  impactAssessment;
    private Boolean capaRequired;
    private String  capaReference;
    private Boolean regulatoryReportable;
}
