package com.qms.module.reports.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Summary statistics block returned at the top of every report response
 * and included in the header of every export.
 */
@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportSummary {

    private String        reportType;          // CAPA / DEVIATION / INCIDENT / USER
    private String        reportTitle;
    private LocalDate     periodFrom;
    private LocalDate     periodTo;
    private LocalDateTime generatedAt;
    private String        generatedBy;

    private long          totalRecords;
    private long          openCount;
    private long          inProgressCount;
    private long          closedCount;
    private long          overdueCount;
    private long          criticalCount;

    private Double        avgResolutionDays;
    private Double        overdueRate;         // overdueCount / totalRecords * 100

    // ── Breakdown charts ─────────────────────────────────────
    private List<AggregationResult> byStatus;
    private List<AggregationResult> byPriority;
    private List<AggregationResult> byDepartment;
    private List<AggregationResult> monthlyTrend;  // count per calendar month
}
