package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "Assigns (replaces) the complete set of roles for a user")
public class AssignRolesRequest {

    @NotEmpty(message = "At least one role ID is required")
    @Schema(description = "Complete set of role IDs — replaces any existing assignments",
            example = "[1, 3]")
    private Set<Long> roleIds;
}
