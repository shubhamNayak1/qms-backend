package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Grant or revoke a permission for a single user, overriding the role defaults.")
public class UserPermissionOverrideRequest {

    @NotNull
    @Schema(description = "Permission id to override", example = "42")
    private Long permissionId;

    @NotNull
    @Schema(description = "GRANT (add on top of role perms) or REVOKE (strip even if role grants it)",
            example = "GRANT")
    private String grantType;

    @Schema(description = "Audit reason captured on the row.")
    private String reason;
}
