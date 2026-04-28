package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.lms.enums.ComplianceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComplianceSubmissionResponse {

    private Long             id;
    private Long             enrollmentId;
    private Long             userId;
    private String           userName;

    private String           attachmentStorageKey;
    private String           attachmentFileName;
    private Long             attachmentFileSizeBytes;
    private String           qnaAnswers;

    private ComplianceStatus status;
    private String           rejectionReason;

    private LocalDateTime    submittedAt;
    private LocalDateTime    updatedAt;

    // Review details (if reviewed)
    private String           reviewDecision;
    private String           reviewerName;
    private String           reviewerRole;
    private String           reviewComments;
    private LocalDateTime    reviewedAt;
}
