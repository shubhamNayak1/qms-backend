package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TniResponse {

    private Long          id;
    private Long          enrollmentId;
    private Long          userId;
    private String        userName;
    private String        department;
    private String        designation;
    private String        jobDescription;
    private String        identifiedGaps;
    private String        recommendedTrainings;
    private String        notes;
    private String        generatedBy;
    private LocalDateTime generatedAt;
    private LocalDateTime updatedAt;
}
