package com.qms.module.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PermissionResponse {
    private Long   id;
    private String name;
    private String displayName;
    private String module;
    private String description;
    private LocalDateTime createdAt;
    private String createdBy;
}
