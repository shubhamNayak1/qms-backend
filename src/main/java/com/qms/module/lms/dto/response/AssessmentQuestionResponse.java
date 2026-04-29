package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.lms.enums.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Question returned to trainees during an exam attempt.
 * correctAnswer is intentionally excluded to prevent cheating.
 * correctAnswer is included only in the manager/admin view (via AssessmentDetailResponse).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssessmentQuestionResponse {

    private Long         id;
    private QuestionType questionType;
    private String       questionText;

    /** Parsed list of answer options. Null for SHORT_ANSWER. */
    private List<String> options;

    /** Explanation shown after the attempt is submitted. */
    private String explanation;

    private Integer marks;
    private Integer displayOrder;
}
