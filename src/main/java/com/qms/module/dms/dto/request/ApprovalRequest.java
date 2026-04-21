package com.qms.module.dms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Approve or reject a document that is UNDER_REVIEW")
public class ApprovalRequest {

    @Size(max = 1000, message = "Comments must not exceed 1000 characters")
    @Schema(description = "Approval comments or rejection reason")
    private String comments;
}
