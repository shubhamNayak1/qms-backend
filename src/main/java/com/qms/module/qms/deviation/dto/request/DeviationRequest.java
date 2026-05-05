package com.qms.module.qms.deviation.dto.request;

import com.qms.module.qms.common.dto.request.QmsBaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request body for creating or updating a Deviation record")
public class DeviationRequest extends QmsBaseRequest {

    @Schema(description = "Parent Incident id (every Deviation originates from an Incident where deviation_required = TRUE)")
    private Long parentIncidentId;

    @Schema(example = "Unplanned", description = "Planned or Unplanned")
    private String deviationType;

    @Schema(example = "BATCH-2024-001", description = "Affected product batch/lot number")
    private String productBatch;

    @Schema(example = "Filling Line 3")
    private String processArea;

    private String  impactAssessment;
    private Boolean capaRequired;
    private String  capaReference;

    @Schema(description = "CAPA record number stamped at HOD Assessment when CAPA Required = TRUE")
    private String  linkedCapaNumber;

    private Boolean regulatoryReportable;

    @Schema(description = "Set at the 2nd QA Review pass — routes through PENDING_SITE_HEAD when true")
    private Boolean siteHeadRequired;

    @Schema(description = "Set at the 2nd QA Review pass — routes through PENDING_CUSTOMER_COMMENT (parallel with RA) when true")
    private Boolean customerCommentRequired;

    @Schema(description = "Closure cover-sheet narrative captured at PENDING_VERIFICATION")
    private String  investigationSummary;
}
