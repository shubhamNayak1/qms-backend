package com.qms.module.qms.common.dto.request;

import com.qms.common.enums.QmsStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body for a workflow status transition")
public class WorkflowRequest {

    @NotNull(message = "Target status is required")
    @Schema(description = "The status to transition to", example = "IN_PROGRESS")
    private QmsStatus targetStatus;

    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    @Schema(description = "Optional comment explaining the transition reason")
    private String comment;
}
