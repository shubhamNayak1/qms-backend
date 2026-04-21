package com.qms.module.qms.capa.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Submit effectiveness verification result for a closed CAPA")
public class EffectivenessRequest {

    @NotNull(message = "isEffective is required")
    @Schema(description = "true = CAPA was effective, false = CAPA needs rework")
    private Boolean isEffective;

    @Schema(description = "Detailed explanation of the verification findings")
    private String effectivenessResult;
}
