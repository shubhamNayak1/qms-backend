package com.qms.module.audit.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class AuditStatsResponse {

    // ── Period ───────────────────────────────────────────────
    private LocalDateTime periodFrom;
    private LocalDateTime periodTo;

    // ── Volume ───────────────────────────────────────────────
    private long totalEvents;
    private long successEvents;
    private long failureEvents;

    // ── Auth ─────────────────────────────────────────────────
    private long loginEvents;
    private long loginFailures;

    // ── Breakdown ────────────────────────────────────────────
    /** Event count grouped by AuditModule name */
    private Map<String, Long> moduleBreakdown;

    /** Event count grouped by AuditAction name (optional, populated on request) */
    private Map<String, Long> actionBreakdown;

    // ── Health indicators ────────────────────────────────────
    public double getFailureRate() {
        return totalEvents == 0 ? 0d
                : Math.round((failureEvents * 100.0 / totalEvents) * 100.0) / 100.0;
    }

    public double getLoginFailureRate() {
        long totalLogins = loginEvents + loginFailures;
        return totalLogins == 0 ? 0d
                : Math.round((loginFailures * 100.0 / totalLogins) * 100.0) / 100.0;
    }
}
