package com.qms.module.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class RoleResponse {
    private Long   id;
    private String name;
    private String displayName;
    private String description;
    private Boolean isSystemRole;
    private Set<PermissionResponse> permissions;
    private Long userCount;           // how many active users hold this role
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
