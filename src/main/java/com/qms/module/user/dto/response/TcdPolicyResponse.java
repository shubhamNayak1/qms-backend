package com.qms.module.user.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TcdPolicyResponse {

    private Long    id;
    private String  moduleKey;
    private int     maxDays;
    private int     warningDays;
    private boolean extensionAllowed;
    private int     maxExtensions;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String        createdBy;
    private String        updatedBy;
}
