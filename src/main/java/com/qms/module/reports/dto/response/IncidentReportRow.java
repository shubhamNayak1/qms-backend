package com.qms.module.reports.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class IncidentReportRow {

    private Long          id;
    private String        recordNumber;
    private String        title;
    private String        status;
    private String        priority;
    private String        incidentType;
    private String        severity;
    private String        location;
    private String        department;
    private LocalDate     occurrenceDate;
    private String        reportedBy;
    private String        assignedToName;
    private Boolean       injuryInvolved;
    private String        capaReference;
    private LocalDate     dueDate;
    private LocalDate     closedDate;
    private boolean       overdue;
    private long          ageInDays;
    private LocalDateTime createdAt;
    private String        createdBy;
}
