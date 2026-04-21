package com.qms.module.dms.dto.response;

import com.qms.module.dms.enums.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class VersionSummaryResponse {
    private Long           id;
    private String         version;
    private DocumentStatus status;
    private String         changeSummary;
    private String         authorName;
    private LocalDateTime  createdAt;
    private LocalDateTime  approvedAt;
    private String         approvedByName;
    private Boolean        isCurrent;
}
