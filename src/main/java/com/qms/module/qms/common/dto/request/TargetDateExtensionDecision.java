package com.qms.module.qms.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** HOD / QA Reviewer decides on an Initiator's extension request. */
@Data
@Schema(description = "Approve or reject an extension request")
public class TargetDateExtensionDecision {

    @NotNull
    private Boolean approve;

    @NotBlank(message = "A remark is required for either decision")
    @Size(max = 2000)
    private String remark;
}
