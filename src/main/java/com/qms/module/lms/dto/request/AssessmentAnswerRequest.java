package com.qms.module.lms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Submit assessment answers for grading")
public class AssessmentAnswerRequest {

    /**
     * Map of questionId (Long) → answer (String).
     * For MULTI_SELECT: comma-separated selected options e.g. "Option A,Option C"
     */
    @NotEmpty(message = "answers must not be empty")
    @Schema(example = "{\"1\": \"Option A\", \"2\": \"True\", \"3\": \"Option B,Option D\"}")
    private Map<Long, String> answers;
}
