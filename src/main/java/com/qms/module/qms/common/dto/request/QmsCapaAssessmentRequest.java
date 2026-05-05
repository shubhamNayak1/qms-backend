package com.qms.module.qms.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request for the responsible dept to fill a single CAPA effectiveness-assessment cycle row")
public class QmsCapaAssessmentRequest {

    @Schema(description = "What the dept observed when assessing CAPA effectiveness for this cycle")
    private String actionObserved;

    @Schema(description = "DMS / file reference for evidence backing the assessment")
    private String evidenceRef;

    @Schema(description = "Was the CAPA effective for this cycle?")
    private Boolean isEffective;
}
