package com.qms.module.reports.aggregation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.module.reports.entity.SavedReport;
import com.qms.module.reports.enums.ReportModule;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Builds and executes dynamic SQL queries based on SavedReport configuration.
 *
 * Two modes:
 *  - GROUPED  : dimensions selected → GROUP BY dimensions, COUNT(*) per group
 *  - DETAIL   : no dimensions → flat rows with metric columns
 *
 * Returns List<Map<String,Object>> — each map is one result row, keys = column labels.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicReportBuilder {

    private final EntityManager entityManager;
    private final ObjectMapper  objectMapper;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(SavedReport report) {
        ReportModule module     = report.getModule();
        String       table      = ModuleFieldRegistry.tableFor(module);
        List<String> dimKeys    = parseJson(report.getDimensions());
        List<String> metricKeys = parseJson(report.getMetrics());

        boolean grouped = dimKeys != null && !dimKeys.isEmpty();

        StringBuilder sql = new StringBuilder("SELECT ");

        List<String> selectParts = new ArrayList<>();
        List<String> groupByParts = new ArrayList<>();
        List<String> labelList = new ArrayList<>();

        if (grouped) {
            for (String key : dimKeys) {
                ModuleFieldRegistry.FieldDef f = ModuleFieldRegistry.findField(module, key);
                selectParts.add(f.getSqlExpr() + " AS \"" + f.getLabel() + "\"");
                groupByParts.add(f.getSqlExpr());
                labelList.add(f.getLabel());
            }
            selectParts.add("COUNT(*) AS \"Count\"");
            labelList.add("Count");
        } else {
            // detail mode — show all metrics (or defaults if none specified)
            List<String> keys = (metricKeys != null && !metricKeys.isEmpty())
                    ? metricKeys
                    : ModuleFieldRegistry.metricsFor(module).stream()
                          .map(ModuleFieldRegistry.FieldDef::getKey).toList();
            for (String key : keys) {
                ModuleFieldRegistry.FieldDef f = ModuleFieldRegistry.findField(module, key);
                selectParts.add(f.getSqlExpr() + " AS \"" + f.getLabel() + "\"");
                labelList.add(f.getLabel());
            }
        }

        // Additional metric columns in grouped mode
        if (grouped && metricKeys != null && !metricKeys.isEmpty()) {
            for (String key : metricKeys) {
                ModuleFieldRegistry.FieldDef f = ModuleFieldRegistry.findField(module, key);
                // For grouped mode, wrap non-groupable expressions as first-value aggregates
                selectParts.add("MIN(" + f.getSqlExpr() + ") AS \"" + f.getLabel() + "\"");
                labelList.add(f.getLabel());
            }
        }

        sql.append(String.join(", ", selectParts));
        sql.append(" FROM ").append(table);
        sql.append(" WHERE is_deleted = false");

        // Date range filter
        if (report.getDateFrom() != null) {
            sql.append(" AND created_at >= '").append(report.getDateFrom()).append("'");
        }
        if (report.getDateTo() != null) {
            // include full end day
            sql.append(" AND created_at < '").append(report.getDateTo().plusDays(1)).append("'");
        }

        if (grouped) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByParts));
            sql.append(" ORDER BY ").append(groupByParts.get(0));
        } else {
            sql.append(" ORDER BY created_at DESC");
        }

        sql.append(" LIMIT 50000");

        log.debug("DynamicReportBuilder SQL: {}", sql);

        @SuppressWarnings("unchecked")
        List<Object[]> rawRows = entityManager.createNativeQuery(sql.toString()).getResultList();

        List<Map<String, Object>> result = new ArrayList<>(rawRows.size());
        for (Object[] row : rawRows) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < labelList.size() && i < row.length; i++) {
                map.put(labelList.get(i), row[i]);
            }
            result.add(map);
        }
        return result;
    }

    private List<String> parseJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
