package com.qms.module.reports.dto.request;

import com.qms.module.reports.enums.ExportFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class UpdateReportRequest {

    private String      name;
    private String      description;
    private ExportFormat format;
    private LocalDate   dateFrom;
    private LocalDate   dateTo;
    private List<String> dimensions;
    private List<String> metrics;
    private Map<String, String> extraFilters;
}
