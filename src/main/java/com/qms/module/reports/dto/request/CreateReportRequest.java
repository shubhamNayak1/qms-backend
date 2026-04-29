package com.qms.module.reports.dto.request;

import com.qms.module.reports.enums.ExportFormat;
import com.qms.module.reports.enums.ReportModule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateReportRequest {

    @NotBlank(message = "Report name is required")
    private String name;

    private String description;

    @NotNull(message = "Module is required")
    private ReportModule module;

    @NotNull(message = "Export format is required (EXCEL or CSV)")
    private ExportFormat format;

    /** Date range — rows created within this window are included */
    private LocalDate dateFrom;
    private LocalDate dateTo;

    /**
     * Dimension field keys (from GET /reports/modules/{module}/fields).
     * Data will be grouped by these fields. Leave empty for detail mode.
     */
    private List<String> dimensions;

    /**
     * Metric field keys to include in the report.
     * In grouped mode: shown as aggregate (MIN value per group).
     * In detail mode: each key becomes a column.
     * Leave empty to include all default metrics.
     */
    private List<String> metrics;

    /** Optional extra filters as key=value map (e.g. {"status":"OPEN","priority":"HIGH"}) */
    private java.util.Map<String, String> extraFilters;
}
