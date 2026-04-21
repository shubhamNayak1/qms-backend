package com.qms.module.reports.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Extends ReportFilter with export-specific settings.
 *
 * Sent as JSON body to the export endpoints:
 *   POST /api/v1/reports/{type}/export/excel
 *   POST /api/v1/reports/{type}/export/pdf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Report filter + export format settings")
public class ExportRequest extends ReportFilter {

    @NotNull(message = "format is required")
    @Schema(description = "Export format", example = "EXCEL", allowableValues = {"EXCEL", "PDF"})
    private ExportFormat format;

    @Schema(description = "Columns to include (null = all columns)")
    private List<String> columns;

    @Schema(description = "Report title shown in the exported document",
            example = "CAPA Status Report — Q1 2024")
    private String reportTitle;

    @Schema(description = "Include summary statistics block at top of export", example = "true")
    private boolean includeSummary = true;

    public enum ExportFormat { EXCEL, PDF }
}
