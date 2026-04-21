package com.qms.module.qms.common.service;

import com.qms.common.enums.QmsRecordType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates unique, sequential record numbers for all QMS sub-modules.
 *
 * Format:  {PREFIX}-{YYYYMM}-{NNNN}
 * Example: CAPA-202404-0001 / DEV-202404-0023 / INC-202404-0007
 *
 * The sequence is per-module, per-month. It resets at the start of each month.
 * The implementation queries the actual DB table to determine the next number,
 * which avoids race conditions in concurrent environments.
 *
 * Each sub-module service injects this and calls generate(type, tableName).
 */
@Component
@RequiredArgsConstructor
public class RecordNumberGenerator {

    private final JdbcTemplate jdbcTemplate;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * @param type      the QMS sub-module type (determines the prefix)
     * @param tableName the actual DB table name (e.g. "qms_capa")
     */
    public String generate(QmsRecordType type, String tableName) {
        String yearMonth = MONTH_FMT.format(LocalDate.now());
        String prefix    = type.getPrefix() + "-" + yearMonth + "-";

        // Count existing records for this prefix to find the next sequence number
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName +
                " WHERE record_number LIKE ?",
                Integer.class,
                prefix + "%"
        );

        int next = (count == null ? 0 : count) + 1;
        return prefix + String.format("%04d", next);
    }
}
