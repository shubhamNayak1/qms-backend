package com.qms.module.qms.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request for the QA Reviewer to accept or reject a CAPA effectiveness-assessment cycle row")
public class QmsCapaAssessmentReviewRequest {

    @Schema(example = "ACCEPTED", description = "ACCEPTED or REJECTED")
    private String decision;

    @Schema(description = "Decision narrative — recorded on the audit trail")
    private String comment;
}
