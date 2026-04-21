package com.qms.module.reports.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Unified QMS executive dashboard — aggregates all five QMS modules
 * plus DMS and LMS into a single API response for the main dashboard page.
 */
@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class QmsDashboardResponse {

    private LocalDateTime generatedAt;

    // ── CAPA ─────────────────────────────────────────────────
    private long capaTotal;
    private long capaOpen;
    private long capaInProgress;
    private long capaPendingApproval;
    private long capaClosed;
    private long capaOverdue;
    private long capaCritical;

    // ── Deviation ────────────────────────────────────────────
    private long deviationTotal;
    private long deviationOpen;
    private long deviationInProgress;
    private long deviationClosed;
    private long deviationOverdue;
    private long deviationRegulatoryReportable;

    // ── Incident ─────────────────────────────────────────────
    private long incidentTotal;
    private long incidentOpen;
    private long incidentInProgress;
    private long incidentClosed;
    private long incidentOverdue;
    private long incidentCriticalSeverity;
    private long incidentInjuryInvolved;

    // ── Change Control ───────────────────────────────────────
    private long changeControlTotal;
    private long changeControlOpen;
    private long changeControlPendingApproval;
    private long changeControlClosed;
    private long changeControlOverdue;

    // ── Market Complaint ─────────────────────────────────────
    private long complaintTotal;
    private long complaintOpen;
    private long complaintInProgress;
    private long complaintClosed;
    private long complaintOverdue;
    private long complaintReportableToAuthority;

    // ── Cross-module aggregates ──────────────────────────────
    private long totalOpenRecords;
    private long totalOverdueRecords;
    private long totalCriticalRecords;

    // ── Trend data (last 6 months, all modules combined) ─────
    private List<AggregationResult> monthlyOpenTrend;
    private List<AggregationResult> monthlyClosedTrend;

    // ── Department breakdown (open records) ──────────────────
    private List<AggregationResult> openByDepartment;

    // ── Priority breakdown (all open records) ────────────────
    private Map<String, Long> openByPriority;

    // ── System health KPIs ───────────────────────────────────
    private double overdueRate;          // totalOverdue / totalOpen * 100
    private double avgCapaResolutionDays;
    private double avgDeviationResolutionDays;
    private double avgIncidentResolutionDays;
}
