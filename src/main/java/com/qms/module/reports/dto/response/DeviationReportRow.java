package com.qms.module.reports.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviationReportRow {

    private Long          id;
    private String        recordNumber;
    private String        title;
    private String        status;
    private String        priority;
    private String        deviationType;
    private String        productBatch;
    private String        processArea;
    private String        department;
    private String        assignedToName;
    private String        raisedByName;
    private Boolean       capaRequired;
    private String        capaReference;
    private Boolean       regulatoryReportable;
    private String        impactAssessment;
    private LocalDate     dueDate;
    private LocalDate     closedDate;
    private boolean       overdue;
    private long          ageInDays;
    private LocalDateTime createdAt;
    private String        createdBy;
}
