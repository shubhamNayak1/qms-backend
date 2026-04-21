package com.qms.module.dms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Obsolete an EFFECTIVE document — reason is mandatory for audit trail")
public class ObsoleteRequest {

    @NotBlank(message = "Reason for obsoleting is required")
    @Schema(example = "Replaced by revised procedure SOP-001 v3.0", required = true)
    private String reason;
}
