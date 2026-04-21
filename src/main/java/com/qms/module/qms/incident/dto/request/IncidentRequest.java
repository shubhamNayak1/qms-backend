package com.qms.module.qms.incident.dto.request;

import com.qms.module.qms.common.dto.request.QmsBaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Request body for creating or updating an Incident record")
public class IncidentRequest extends QmsBaseRequest {

    @Schema(example = "Safety", description = "Safety / Quality / Environmental / Equipment / Personnel")
    private String incidentType;

    @Schema(example = "Major", description = "Minor / Major / Critical")
    private String severity;

    @Schema(example = "Building B, Floor 2")
    private String location;

    private LocalDate occurrenceDate;
    private String    reportedBy;
    private String    immediateAction;
    private String    investigationDetails;
    private String    capaReference;
    private Boolean   injuryInvolved;
    private String    injuryDetails;
}
