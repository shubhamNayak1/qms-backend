package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.lms.enums.ProgramStatus;
import com.qms.module.lms.enums.TrainingSubType;
import com.qms.module.lms.enums.TrainingType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProgramResponse {

    private Long           id;
    private String         code;
    private String         title;
    private String         description;

    // ── Training type ─────────────────────────────────────────
    private TrainingType    trainingType;
    private TrainingSubType trainingSubType;

    // ── Classification ────────────────────────────────────────
    private String         category;
    private String         department;
    private String         departments;
    private String         tags;
    private ProgramStatus  status;
    private Boolean        isMandatory;

    // ── Trainer / coordinator / location ──────────────────────
    private Long           trainerId;
    private String         trainerName;
    private String         vendorName;
    private Long           coordinatorId;
    private String         coordinatorName;
    private String         location;
    private String         conferenceLink;

    // ── Exam ──────────────────────────────────────────────────
    private Boolean        examEnabled;
    private Boolean        assessmentRequired;  // backward compat alias
    private Integer        passScore;
    private Integer        maxAttempts;

    // ── Duration / certificate ────────────────────────────────
    private Integer        estimatedDurationMinutes;
    private Integer        certificateValidityYears;
    private Integer        completionDeadlineDays;

    // ── Review ────────────────────────────────────────────────
    private String         rejectionReason;

    // ── Ownership ─────────────────────────────────────────────
    private String         ownerName;

    // ── Stats ─────────────────────────────────────────────────
    private long           totalEnrollments;
    private long           completedEnrollments;
    private Double         complianceRate;

    // ── Related data ──────────────────────────────────────────
    private List<ContentItemSummary>   contents;
    private List<DocumentLinkSummary>  documentLinks;
    private List<SessionSummary>       sessions;
    private Boolean                    hasAssessment;

    private LocalDateTime  createdAt;
    private LocalDateTime  updatedAt;
    private String         createdBy;

    // ── Nested summaries ──────────────────────────────────────

    @Data @Builder
    public static class ContentItemSummary {
        private Long    id;
        private String  contentType;
        private String  title;
        private Integer displayOrder;
        private Boolean isRequired;
        private Integer durationMinutes;
        private String  dmsDocNumber;
    }

    @Data @Builder
    public static class DocumentLinkSummary {
        private Long    id;
        private Long    dmsDocumentId;
        private String  dmsDocNumber;
        private String  dmsDocVersion;
        private String  dmsDocTitle;
        private Boolean triggerReviewOnUpdate;
    }

    @Data @Builder
    public static class SessionSummary {
        private Long          id;
        private String        sessionDate;
        private String        sessionEndDate;
        private String        venue;
        private String        meetingLink;
        private String        trainerName;
        private String        status;
        private long          presentCount;
    }
}
