package com.qms.module.reports.export;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exports dynamic report data as RFC 4180 CSV.
 */
@Component
public class CsvExporter {

    public byte[] export(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return "No data\n".getBytes();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter pw = new PrintWriter(baos)) {
            // Header row
            List<String> headers = new ArrayList<>(rows.get(0).keySet());
            pw.println(toCsvLine(headers));

            // Data rows
            for (Map<String, Object> row : rows) {
                List<String> vals = headers.stream()
                        .map(h -> row.get(h))
                        .map(v -> v == null ? "" : v.toString())
                        .toList();
                pw.println(toCsvLine(vals));
            }
        }
        return baos.toByteArray();
    }

    private String toCsvLine(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(values.get(i)));
        }
        return sb.toString();
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
