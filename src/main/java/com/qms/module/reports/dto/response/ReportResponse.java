package com.qms.module.reports.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.reports.enums.ExportFormat;
import com.qms.module.reports.enums.ReportModule;
import com.qms.module.reports.enums.ReportStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportResponse {
    private Long         id;
    private String       name;
    private String       description;
    private ReportModule module;
    private ExportFormat format;
    private LocalDate    dateFrom;
    private LocalDate    dateTo;
    private List<String> dimensions;
    private List<String> metrics;
    private ReportStatus status;
    private String       fileName;
    private Long         fileSizeBytes;
    private LocalDateTime lastRunAt;
    private Integer       runCount;
    private String        lastRunError;
    private Boolean       isDisabled;
    private String        createdByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Present only when downloadable */
    private String        downloadUrl;
}
