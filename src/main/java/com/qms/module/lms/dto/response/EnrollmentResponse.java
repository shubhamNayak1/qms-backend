package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.lms.enums.EnrollmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrollmentResponse {

    private Long             id;
    private Long             userId;
    private String           userName;
    private String           userEmail;
    private String           userDepartment;

    private Long             programId;
    private String           programCode;
    private String           programTitle;

    private EnrollmentStatus status;
    private LocalDate        dueDate;
    private LocalDateTime    startedAt;
    private LocalDateTime    completedAt;

    private Integer          progressPercent;
    private Integer          attemptsUsed;
    private Integer          lastScore;

    private String           assignedByName;
    private String           assignmentReason;
    private String           waiverReason;
    private String           waivedByName;

    private boolean          overdue;
    private boolean          compliant;

    private List<ContentProgressSummary> contentProgress;
    private CertificateResponse          certificate;

    private LocalDateTime    createdAt;
    private LocalDateTime    updatedAt;

    @Data @Builder
    public static class ContentProgressSummary {
        private Long    contentId;
        private String  contentTitle;
        private String  contentType;
        private Boolean isCompleted;
        private Boolean acknowledged;
        private Integer viewPercent;
        private Integer displayOrder;
        private Boolean isRequired;
    }
}
