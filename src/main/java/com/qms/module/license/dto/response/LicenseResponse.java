package com.qms.module.license.dto.response;

import com.qms.module.license.enums.LicenseStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LicenseResponse {
    private Long          id;
    private String        code;
    private LicenseStatus status;
    private Long          assignedToUserId;
    private String        assignedToUsername;   // resolved on read
    private LocalDateTime assignedAt;
    private Long          assignedByUserId;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long          revokedByUserId;
    private String        notes;
    private LocalDateTime createdAt;
}
