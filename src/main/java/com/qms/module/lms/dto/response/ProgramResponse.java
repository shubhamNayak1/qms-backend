package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.lms.enums.ProgramStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProgramResponse {

    private Long          id;
    private String        code;
    private String        title;
    private String        description;
    private String        category;
    private String        department;
    private String        tags;
    private ProgramStatus status;
    private Boolean       isMandatory;
    private Integer       estimatedDurationMinutes;
    private Integer       certificateValidityYears;
    private Integer       completionDeadlineDays;
    private Boolean       assessmentRequired;
    private Integer       passScore;
    private Integer       maxAttempts;
    private String        ownerName;

    // Summary stats
    private long totalEnrollments;
    private long completedEnrollments;
    private Double complianceRate;

    // Content outline
    private List<ContentItemSummary> contents;
    private List<DocumentLinkSummary> documentLinks;
    private Boolean hasAssessment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String        createdBy;

    @Data @Builder
    public static class ContentItemSummary {
        private Long    id;
        private String  contentType;
        private String  title;
        private Integer displayOrder;
        private Boolean isRequired;
        private Integer durationMinutes;
        private String  dmsDocNumber;   // populated for DOCUMENT type
    }

    @Data @Builder
    public static class DocumentLinkSummary {
        private Long   id;
        private Long   dmsDocumentId;
        private String dmsDocNumber;
        private String dmsDocVersion;
        private String dmsDocTitle;
        private Boolean triggerReviewOnUpdate;
    }
}
