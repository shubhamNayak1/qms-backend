package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body for creating a new permission")
public class CreatePermissionRequest {

    @NotBlank(message = "Permission name is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
             message = "Permission name must be UPPER_SNAKE_CASE (e.g. USER_CREATE)")
    @Size(max = 100)
    @Schema(example = "CAPA_APPROVE")
    private String name;

    @NotBlank(message = "Display name is required")
    @Size(max = 150)
    @Schema(example = "Approve CAPA Records")
    private String displayName;

    @NotBlank(message = "Module is required")
    @Size(max = 50)
    @Schema(example = "CAPA", description = "Module this permission belongs to: USER, CAPA, DMS, LMS, REPORT")
    private String module;

    @Size(max = 255)
    private String description;
}
