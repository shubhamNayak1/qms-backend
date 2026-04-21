package com.qms.module.dms.dto.response;

import com.qms.module.dms.enums.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApprovalResponse {
    private Long           id;
    private Long           approverId;
    private String         approverName;
    private String         approverRole;
    private DocumentStatus decision;
    private String         comments;
    private LocalDateTime  decidedAt;
    private Integer        reviewCycle;
    private LocalDateTime  createdAt;
}
