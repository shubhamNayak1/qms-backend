package com.qms.module.qms.dashboard.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class QmsDashboardResponse {

    private LocalDateTime generatedAt;

    // ── CAPA KPIs ─────────────────────────────────────────────
    private long capaOpen;
    private long capaInProgress;
    private long capaPendingApproval;
    private long capaClosed;
    private long capaOverdue;
    private long capaCritical;

    // ── Deviation KPIs ────────────────────────────────────────
    private long deviationOpen;
    private long deviationInProgress;
    private long deviationClosed;
    private long deviationOverdue;

    // ── Incident KPIs ─────────────────────────────────────────
    private long incidentOpen;
    private long incidentInProgress;
    private long incidentClosed;
    private long incidentOverdue;
    private long incidentCriticalSeverity;

    // ── Change Control KPIs ───────────────────────────────────
    private long changeControlOpen;
    private long changeControlPendingApproval;
    private long changeControlClosed;
    private long changeControlOverdue;

    // ── Market Complaint KPIs ─────────────────────────────────
    private long complaintOpen;
    private long complaintInProgress;
    private long complaintClosed;
    private long complaintOverdue;
    private long complaintReportableToAuthority;

    // ── Aggregates ────────────────────────────────────────────
    private long totalOpenRecords;
    private long totalOverdueRecords;

    /** Count of records grouped by status across all modules */
    private Map<String, Long> statusBreakdown;

    /** Count of records grouped by priority across all modules */
    private Map<String, Long> priorityBreakdown;
}
