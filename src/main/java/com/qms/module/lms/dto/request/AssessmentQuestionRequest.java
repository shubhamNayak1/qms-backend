package com.qms.module.lms.dto.request;

import com.qms.module.lms.enums.QuestionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssessmentQuestionRequest {

    @NotNull(message = "questionType is required")
    private QuestionType questionType;

    @NotBlank(message = "questionText is required")
    private String questionText;

    /**
     * JSON array string of answer options. Required for MULTIPLE_CHOICE, MULTI_SELECT, TRUE_FALSE.
     * Example: ["Option A","Option B","Option C","Option D"]
     * For TRUE_FALSE use: ["True","False"]
     */
    private String options;

    /**
     * The correct answer. Required for MULTIPLE_CHOICE, MULTI_SELECT, TRUE_FALSE.
     * For MULTI_SELECT: comma-separated, e.g. "Option A,Option C"
     * Null for SHORT_ANSWER (manually graded).
     */
    private String correctAnswer;

    /** Explanation shown to trainee after submission. Optional. */
    private String explanation;

    /** Marks awarded for a correct answer. Defaults to 1. */
    @Min(value = 1, message = "marks must be at least 1")
    private Integer marks = 1;

    /** Order this question appears within the assessment. Defaults to 1. */
    @Min(value = 1, message = "displayOrder must be at least 1")
    private Integer displayOrder = 1;
}
