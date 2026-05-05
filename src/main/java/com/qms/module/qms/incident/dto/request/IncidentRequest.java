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

    @Schema(description = "Set at HOD Assessment — drives the CAPA cross-link")
    private Boolean   capaRequired;

    @Schema(description = "CAPA record number stamped at HOD Assessment when capaRequired = TRUE")
    private String    linkedCapaNumber;

    private Boolean   injuryInvolved;
    private String    injuryDetails;

    @Schema(example = "LABORATORY", description = "LABORATORY (OOS/OOT) or GENERAL incident sub-type")
    private String  incidentSubType;

    @Schema(description = "Lab branch fork — drives the Lab + Retest vs Lab + No-Retest path")
    private Boolean retestingRequired;

    @Schema(description = "General branch fork — TRUE = spawns a Deviation; Incident terminates at DEVIATION_SPAWNED")
    private Boolean deviationRequired;

    @Schema(description = "Set at Assessment by QA — routes through PENDING_SITE_HEAD when true")
    private Boolean siteHeadRequired;

    @Schema(description = "Lab + No-Retest path only — the 'Abnormality in Proposed RA' narrative")
    private String  abnormalityRemedialAction;

    @Schema(description = "Closure verification narrative captured at PENDING_VERIFICATION")
    private String  verificationNarrative;
}
