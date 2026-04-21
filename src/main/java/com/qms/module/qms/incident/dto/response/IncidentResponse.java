package com.qms.module.qms.incident.dto.response;

import com.qms.module.qms.common.dto.response.QmsBaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class IncidentResponse extends QmsBaseResponse {
    private String    incidentType;
    private String    severity;
    private String    location;
    private LocalDate occurrenceDate;
    private String    reportedBy;
    private String    immediateAction;
    private String    investigationDetails;
    private String    capaReference;
    private Boolean   injuryInvolved;
    private String    injuryDetails;
}
