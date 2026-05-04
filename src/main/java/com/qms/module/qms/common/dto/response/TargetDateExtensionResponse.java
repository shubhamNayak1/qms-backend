package com.qms.module.qms.common.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class TargetDateExtensionResponse {
    private Long          recordId;
    private LocalDate     previousTargetDate;   // captured at request time for the audit trail
    private LocalDate     extensionDate;
    private String        reason;
    private String        status;               // PENDING / APPROVED / REJECTED
    private Long          requestedById;
    private LocalDateTime requestedAt;
    private Long          decidedById;
    private LocalDateTime decidedAt;
    private String        decisionRemark;       // captured into the record's comments column
}
