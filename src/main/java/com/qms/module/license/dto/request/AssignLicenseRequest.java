package com.qms.module.license.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Assign a license (by id) to a user")
public class AssignLicenseRequest {

    @NotNull
    @Schema(example = "42")
    private Long userId;
}
