package com.qms.module.lms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Grant a training waiver — the enrollment counts as compliant without completion")
public class WaiverRequest {

    @NotBlank(message = "Waiver reason is required")
    @Schema(example = "User has demonstrated equivalent competency via prior learning assessment (PLA-2024-007)")
    private String reason;
}
