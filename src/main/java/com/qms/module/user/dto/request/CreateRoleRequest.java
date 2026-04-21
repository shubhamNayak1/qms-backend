package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "Request body for creating a new role")
public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
             message = "Role name must be UPPER_SNAKE_CASE (e.g. QA_MANAGER)")
    @Size(max = 60, message = "Role name must not exceed 60 characters")
    @Schema(example = "QA_OFFICER")
    private String name;

    @NotBlank(message = "Display name is required")
    @Size(max = 100)
    @Schema(example = "QA Officer")
    private String displayName;

    @Size(max = 255)
    private String description;

    @Schema(description = "Permission IDs to assign to this role at creation")
    private Set<Long> permissionIds;
}
