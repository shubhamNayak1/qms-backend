package com.qms.module.reports.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Generic aggregation result used for charts, breakdowns, and trend data.
 *
 * Examples:
 *   { "label": "OPEN",      "count": 23, "percentage": 38.3 }
 *   { "label": "HIGH",      "count": 15 }
 *   { "label": "2024-03",   "count": 7  }  ← monthly trend point
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AggregationResult {

    private String  label;        // group key — status, priority, month, department, etc.
    private Long    count;        // number of records in this group
    private Double  percentage;   // count / total * 100 (populated by service)
    private Long    totalDays;    // sum of age in days (for avg resolution time)
    private Double  avgDays;      // average age / resolution days

    /** Nested breakdown within this group (e.g. status breakdown per department). */
    private List<AggregationResult> breakdown;

    /** Arbitrary extra metrics for this group. */
    private Map<String, Object> extras;

    // ── Factory helpers ───────────────────────────────────────

    public static AggregationResult of(String label, Long count) {
        return AggregationResult.builder().label(label).count(count).build();
    }

    public static AggregationResult of(String label, Long count, double pct) {
        return AggregationResult.builder().label(label).count(count)
                .percentage(Math.round(pct * 100.0) / 100.0).build();
    }
}
