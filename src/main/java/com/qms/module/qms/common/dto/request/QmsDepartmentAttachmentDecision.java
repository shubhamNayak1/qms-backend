package com.qms.module.qms.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Head QA's decision on a department attachment row — accept or reject")
public class QmsDepartmentAttachmentDecision {

    @Schema(example = "true", description = "TRUE = APPROVED, FALSE = REJECTED (sends the row back to the department for re-upload)")
    private Boolean approve;

    @Schema(description = "Decision narrative — recorded on the audit trail")
    private String comment;
}
