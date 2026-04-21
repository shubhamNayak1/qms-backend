package com.qms.module.lms.dto.request;

import com.qms.module.lms.enums.ProgramStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Request body for creating or updating a Training Program")
public class ProgramRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 30, message = "Code must not exceed 30 characters")
    @Schema(example = "GMP-001")
    private String code;

    @NotBlank(message = "Title is required")
    @Size(max = 255)
    @Schema(example = "GMP Awareness Training — Level 1")
    private String title;

    private String description;

    @Schema(example = "GMP")
    private String category;

    @Schema(example = "Quality Assurance")
    private String department;

    @Schema(example = "GMP,mandatory,awareness,all-staff")
    private String tags;

    private ProgramStatus status;

    private Boolean isMandatory;

    @Min(1) @Max(9999)
    private Integer estimatedDurationMinutes;

    @Min(1) @Max(10)
    private Integer certificateValidityYears;

    @Min(1) @Max(365)
    private Integer completionDeadlineDays;

    private Boolean assessmentRequired;

    @Min(0) @Max(100)
    private Integer passScore;

    @Min(1) @Max(10)
    private Integer maxAttempts;

    private Long ownerId;
}
