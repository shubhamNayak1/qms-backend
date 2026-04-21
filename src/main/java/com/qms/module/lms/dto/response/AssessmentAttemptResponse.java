package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.lms.enums.AssessmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssessmentAttemptResponse {

    private Long             id;
    private Long             enrollmentId;
    private Integer          attemptNumber;
    private AssessmentStatus status;
    private Integer          rawScore;
    private Integer          totalMarks;
    private Integer          scorePercent;
    private Boolean          passed;
    private Integer          passScore;
    private String           reviewerComments;
    private LocalDateTime    startedAt;
    private LocalDateTime    submittedAt;
    private LocalDateTime    reviewedAt;
    private String           reviewedBy;
}
