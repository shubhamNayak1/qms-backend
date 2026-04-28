package com.qms.module.lms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Training Need Identification data (created after induction approval)")
public class TniRequest {

    @Schema(description = "Employee's Job Description summary used to identify gaps")
    private String jobDescription;

    @Schema(description = "Identified training gaps for this employee")
    private String identifiedGaps;

    /**
     * JSON array of recommended programs, e.g.:
     * [{"code":"GMP-001","title":"GMP Awareness"}]
     */
    @Schema(description = "JSON array of recommended training program codes and titles")
    private String recommendedTrainings;

    private String designation;
    private String notes;
}
