package com.qms.module.reports.service;

import com.qms.common.exception.AppException;
import com.qms.module.reports.aggregation.DynamicReportBuilder;
import com.qms.module.reports.dto.response.ReportInsightResponse;
import com.qms.module.reports.entity.SavedReport;
import com.qms.module.reports.enums.ReportStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportInsightService {

    private final DynamicReportBuilder reportBuilder;

    @Transactional(readOnly = true)
    public ReportInsightResponse getInsights(SavedReport report) {
        if (report.getStatus() == ReportStatus.DISABLED) {
            throw AppException.badRequest("Cannot generate insights for a disabled report.");
        }

        List<Map<String, Object>> rows = reportBuilder.execute(report);

        if (rows.isEmpty()) {
            return ReportInsightResponse.builder()
                    .reportId(report.getId())
                    .reportName(report.getName())
                    .totalRows(0L)
                    .insights(List.of("No data found for the selected date range and filters."))
                    .build();
        }

        // ── Top / Bottom groups (by Count column if grouped) ──
        List<Map<String, Object>> topGroups    = Collections.emptyList();
        List<Map<String, Object>> bottomGroups = Collections.emptyList();

        if (rows.get(0).containsKey("Count")) {
            List<Map<String, Object>> sorted = rows.stream()
                    .sorted(Comparator.comparingLong(m ->
                            -toLong(m.get("Count"))))
                    .collect(Collectors.toList());
            topGroups    = sorted.subList(0, Math.min(5, sorted.size()));
            bottomGroups = sorted.subList(Math.max(0, sorted.size() - 5), sorted.size());
            Collections.reverse(bottomGroups);
        }

        // ── Column stats for numeric columns ──
        Map<String, ReportInsightResponse.ColumnStat> colStats = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            for (String col : rows.get(0).keySet()) {
                List<Double> nums = rows.stream()
                        .map(r -> r.get(col))
                        .filter(v -> v instanceof Number)
                        .map(v -> ((Number) v).doubleValue())
                        .toList();
                if (!nums.isEmpty()) {
                    double min = nums.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                    double max = nums.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                    double avg = nums.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    colStats.put(col, ReportInsightResponse.ColumnStat.builder()
                            .columnName(col)
                            .min(min).max(max)
                            .avg(Math.round(avg * 100.0) / 100.0)
                            .nonNullCount((long) nums.size())
                            .build());
                }
            }
        }

        // ── Trend: compare first vs second half of rows ──
        String trendDirection = "STABLE";
        if (rows.size() >= 4 && rows.get(0).containsKey("Count")) {
            int mid    = rows.size() / 2;
            long first = rows.subList(0, mid).stream().mapToLong(m -> toLong(m.get("Count"))).sum();
            long second= rows.subList(mid, rows.size()).stream().mapToLong(m -> toLong(m.get("Count"))).sum();
            if (second > first * 1.1)       trendDirection = "INCREASING";
            else if (second < first * 0.9)  trendDirection = "DECREASING";
        }

        // ── Generate insight bullets ──
        List<String> insights = new ArrayList<>();
        insights.add("Total records in report: " + rows.size());

        if (!topGroups.isEmpty()) {
            Map<String, Object> top = topGroups.get(0);
            String topKey = top.entrySet().stream()
                    .filter(e -> !e.getKey().equals("Count"))
                    .map(e -> e.getKey() + " = " + e.getValue())
                    .findFirst().orElse("top group");
            insights.add("Highest concentration: " + topKey + " (" + top.get("Count") + " records)");
        }

        if (!colStats.isEmpty()) {
            colStats.forEach((col, stat) -> {
                if (!col.equals("Count")) {
                    insights.add(col + ": min=" + stat.getMin() + ", max=" + stat.getMax() + ", avg=" + stat.getAvg());
                }
            });
        }

        switch (trendDirection) {
            case "INCREASING" -> insights.add("Volume is trending UPWARD in recent periods — review if escalation is needed.");
            case "DECREASING" -> insights.add("Volume is trending DOWNWARD — improvement may be in progress.");
            default           -> insights.add("Volume appears STABLE across the selected period.");
        }

        long overdueCount = rows.stream()
                .filter(r -> "Yes".equals(r.get("Overdue?")))
                .count();
        if (overdueCount > 0) {
            insights.add(overdueCount + " records are overdue ("
                    + Math.round(overdueCount * 100.0 / rows.size()) + "% of total).");
        }

        return ReportInsightResponse.builder()
                .reportId(report.getId())
                .reportName(report.getName())
                .totalRows((long) rows.size())
                .topGroups(topGroups)
                .bottomGroups(bottomGroups.isEmpty() ? null : bottomGroups)
                .columnStats(colStats.isEmpty() ? null : colStats)
                .trendDirection(trendDirection)
                .insights(insights)
                .build();
    }

    private long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(val)); }
        catch (Exception e) { return 0L; }
    }
}
