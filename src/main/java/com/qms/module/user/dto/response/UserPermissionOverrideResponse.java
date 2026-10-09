package com.qms.module.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserPermissionOverrideResponse {
    private Long          userId;
    private Long          permissionId;
    private String        permissionName;
    private String        permissionDisplay;
    private String        module;
    private String        grantType;      // GRANT | REVOKE
    private String        grantedBy;
    private LocalDateTime grantedAt;
    private String        reason;
}
