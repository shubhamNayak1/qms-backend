package com.qms.module.reports.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Flat row DTO for the CAPA report table / export.
 * Denormalised: all fields needed for display in one object.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CapaReportRow {

    private Long          id;
    private String        recordNumber;
    private String        title;
    private String        status;
    private String        priority;
    private String        source;
    private String        capaType;

    private String        department;
    private String        assignedToName;
    private String        raisedByName;
    private String        approvedByName;

    private LocalDate     dueDate;
    private LocalDate     closedDate;
    private LocalDate     effectivenessCheckDate;
    private Boolean       isEffective;
    private String        linkedDeviationNumber;

    private String        rootCause;
    private String        correctiveAction;

    private boolean       overdue;
    private long          ageInDays;        // days since creation

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String        createdBy;
}
