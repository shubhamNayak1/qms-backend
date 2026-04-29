package com.qms.module.reports.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.reports.enums.RunStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportHistoryResponse {
    private Long          id;
    private Long          reportId;
    private String        triggeredByUsername;
    private RunStatus     status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long          durationMs;
    private Long          rowCount;
    private Long          fileSizeBytes;
    private String        errorMessage;
    private LocalDateTime createdAt;
}
