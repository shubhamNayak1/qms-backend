package com.qms.module.reports.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportInsightResponse {

    private Long   reportId;
    private String reportName;
    private Long   totalRows;

    /** Top 5 groups by count (for grouped reports) */
    private List<Map<String, Object>> topGroups;

    /** Bottom 5 groups by count */
    private List<Map<String, Object>> bottomGroups;

    /** Summary statistics per numeric column */
    private Map<String, ColumnStat> columnStats;

    /** Trend direction: INCREASING, DECREASING, STABLE */
    private String trendDirection;

    /** Human-readable insight bullets */
    private List<String> insights;

    @Data @Builder
    public static class ColumnStat {
        private String columnName;
        private Double min;
        private Double max;
        private Double avg;
        private Long   nonNullCount;
    }
}
