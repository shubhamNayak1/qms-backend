package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full assessment definition returned to managers/admins.
 * Includes correct answers — do NOT expose to trainees.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssessmentDetailResponse {

    private Long    id;
    private Long    programId;
    private String  programTitle;
    private String  title;
    private String  instructions;
    private Integer timeLimitMinutes;
    private Integer passScore;
    private Boolean randomiseQuestions;
    private Boolean randomiseAnswers;
    private Integer totalMarks;
    private Integer questionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<QuestionWithAnswerResponse> questions;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionWithAnswerResponse {
        private Long         id;
        private String       questionType;
        private String       questionText;
        private List<String> options;
        private String       correctAnswer;
        private String       explanation;
        private Integer      marks;
        private Integer      displayOrder;
    }
}
