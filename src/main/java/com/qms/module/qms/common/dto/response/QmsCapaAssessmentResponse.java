package com.qms.module.qms.common.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class QmsCapaAssessmentResponse {
    private Long      id;
    private Long      capaId;
    private Integer   sequenceNo;
    private LocalDate dueDate;
    private String    status;          // PENDING / SUBMITTED / ACCEPTED / REJECTED
    private String    actionObserved;
    private String    evidenceRef;
    private Boolean   isEffective;
    private Long      completedById;
    private String    completedByName;
    private LocalDateTime completedAt;
    private String    reviewStatus;    // ACCEPTED / REJECTED / null
    private String    reviewComment;
    private Long      reviewedById;
    private String    reviewedByName;
    private LocalDateTime reviewedAt;
}
