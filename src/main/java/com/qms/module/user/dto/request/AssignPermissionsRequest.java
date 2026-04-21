package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "Assign (replace) the permission set of a role")
public class AssignPermissionsRequest {

    @NotNull(message = "permissionIds must not be null (pass empty set to clear all)")
    @Schema(description = "Complete set of permission IDs — replaces existing assignments. "
                        + "Pass an empty set to revoke all permissions from the role.",
            example = "[1, 2, 5]")
    private Set<Long> permissionIds;
}
