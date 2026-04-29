package com.qms.module.lms.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Request body for creating or updating the Assessment definition
 * attached to a TrainingProgram.
 */
@Data
public class AssessmentSetupRequest {

    private String title;

    private String instructions;

    /** Time limit in minutes. Null = no time limit. */
    @Min(1)
    private Integer timeLimitMinutes;

    /** Minimum score percentage to pass (0–100). */
    @Min(0) @Max(100)
    private Integer passScore;

    /** Whether to randomise the order of questions per attempt. */
    private Boolean randomiseQuestions;

    /** Whether to randomise the order of answer options per attempt. */
    private Boolean randomiseAnswers;
}
