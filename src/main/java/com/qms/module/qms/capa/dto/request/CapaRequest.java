package com.qms.module.qms.capa.dto.request;

import com.qms.module.qms.common.dto.request.QmsBaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Request body for creating or updating a CAPA record")
public class CapaRequest extends QmsBaseRequest {

    @Schema(example = "Audit", description = "What triggered this CAPA: Audit, Complaint, Deviation, Inspection, Internal")
    private String source;

    @Schema(example = "Corrective", description = "CAPA type: Corrective / Preventive / Both")
    private String capaType;

    @Schema(description = "Description of preventive measures to be taken")
    private String preventiveAction;

    @Schema(description = "Date on which effectiveness will be verified")
    private LocalDate effectivenessCheckDate;

    @Schema(description = "Reference to a linked deviation record, e.g. DEV-202404-0003")
    private String linkedDeviationNumber;
}
