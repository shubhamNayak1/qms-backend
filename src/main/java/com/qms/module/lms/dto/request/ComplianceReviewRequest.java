package com.qms.module.lms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Coordinator / HR / QA Head review decision on a compliance submission")
public class ComplianceReviewRequest {

    @NotBlank(message = "Decision is required")
    @Pattern(regexp = "APPROVED|REJECTED", message = "Decision must be APPROVED or REJECTED")
    @Schema(example = "APPROVED", allowableValues = {"APPROVED", "REJECTED"})
    private String decision;

    @Schema(example = "All evidence verified and accepted.")
    private String comments;
}
