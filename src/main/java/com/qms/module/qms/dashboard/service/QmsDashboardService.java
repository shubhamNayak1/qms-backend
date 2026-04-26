package com.qms.module.qms.dashboard.service;

import com.qms.common.enums.Priority;
import com.qms.common.enums.QmsStatus;
import com.qms.module.qms.capa.repository.CapaRepository;
import com.qms.module.qms.changecontrol.repository.ChangeControlRepository;
import com.qms.module.qms.complaint.repository.MarketComplaintRepository;
import com.qms.module.qms.dashboard.dto.QmsDashboardResponse;
import com.qms.module.qms.deviation.repository.DeviationRepository;
import com.qms.module.qms.incident.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates KPI counts from all five QMS sub-module repositories
 * into a single dashboard response.
 *
 * "Active" records = any status that is not CLOSED or CANCELLED.
 * "Pending review" = any PENDING_* status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QmsDashboardService {

    private final CapaRepository             capaRepository;
    private final DeviationRepository        deviationRepository;
    private final IncidentRepository         incidentRepository;
    private final ChangeControlRepository    changeControlRepository;
    private final MarketComplaintRepository  complaintRepository;

    public QmsDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();

        // ── CAPA ──────────────────────────────────────────────
        long capaDraft       = capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.DRAFT);
        long capaPending     = capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_HOD)
                             + capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_QA_REVIEW)
                             + capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_DEPT_COMMENT)
                             + capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_HEAD_QA);
        long capaClosed      = capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.CLOSED);
        long capaOverdue     = capaRepository.countOverdue(today);
        long capaCritical    = capaRepository.countByPriorityAndIsDeletedFalse(Priority.CRITICAL);

        // ── Deviation ─────────────────────────────────────────
        long devDraft        = deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.DRAFT);
        long devPending      = deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_HOD)
                             + deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_QA_REVIEW)
                             + deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_RA_REVIEW)
                             + deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_SITE_HEAD)
                             + deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_INVESTIGATION)
                             + deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_VERIFICATION);
        long devClosed       = deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.CLOSED);
        long devOverdue      = deviationRepository.countOverdue(today);

        // ── Incident ──────────────────────────────────────────
        long incDraft        = incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.DRAFT);
        long incPending      = incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_HOD)
                             + incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_INVESTIGATION)
                             + incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_ATTACHMENTS)
                             + incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_VERIFICATION)
                             + incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_HEAD_QA);
        long incClosed       = incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.CLOSED);
        long incOverdue      = incidentRepository.countOverdue(today);
        long incCritical     = incidentRepository.countBySeverityAndIsDeletedFalse("Critical");

        // ── Change Control ────────────────────────────────────
        long ccDraft         = changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.DRAFT);
        long ccPending       = changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_HOD)
                             + changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_QA_REVIEW)
                             + changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_DEPT_COMMENT)
                             + changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_RA_REVIEW)
                             + changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_SITE_HEAD)
                             + changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_CUSTOMER_COMMENT)
                             + changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_HEAD_QA)
                             + changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_VERIFICATION);
        long ccClosed        = changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.CLOSED);
        long ccOverdue       = changeControlRepository.countOverdue(today);

        // ── Market Complaint ──────────────────────────────────
        long mcDraft         = complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.DRAFT);
        long mcPending       = complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_HOD)
                             + complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_INVESTIGATION)
                             + complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_ATTACHMENTS)
                             + complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_VERIFICATION);
        long mcClosed        = complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.CLOSED);
        long mcOverdue       = complaintRepository.countOverdue(today);
        long mcReportable    = complaintRepository.countByReportableToAuthorityTrueAndIsDeletedFalse();

        // ── Cross-module aggregates ───────────────────────────
        long totalDraft   = capaDraft   + devDraft   + incDraft   + ccDraft   + mcDraft;
        long totalPending = capaPending + devPending  + incPending + ccPending + mcPending;
        long totalClosed  = capaClosed  + devClosed   + incClosed  + ccClosed  + mcClosed;
        long totalOverdue = capaOverdue + devOverdue  + incOverdue + ccOverdue + mcOverdue;

        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        statusBreakdown.put("DRAFT",           totalDraft);
        statusBreakdown.put("PENDING_REVIEW",  totalPending);
        statusBreakdown.put("CLOSED",          totalClosed);
        statusBreakdown.put("CANCELLED",
                capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.CANCELLED)
              + deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.CANCELLED)
              + incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.CANCELLED)
              + changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.CANCELLED)
              + complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.CANCELLED));
        statusBreakdown.put("REJECTED",
                capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.REJECTED)
              + deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.REJECTED)
              + incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.REJECTED)
              + changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.REJECTED)
              + complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.REJECTED));

        Map<String, Long> priorityBreakdown = new LinkedHashMap<>();
        for (Priority p : Priority.values()) {
            priorityBreakdown.put(p.name(), capaRepository.countByPriorityAndIsDeletedFalse(p));
        }

        return QmsDashboardResponse.builder()
                .generatedAt(LocalDateTime.now())
                // CAPA
                .capaOpen(capaDraft).capaInProgress(capaPending)
                .capaPendingApproval(capaPending).capaClosed(capaClosed)
                .capaOverdue(capaOverdue).capaCritical(capaCritical)
                // Deviation
                .deviationOpen(devDraft).deviationInProgress(devPending)
                .deviationClosed(devClosed).deviationOverdue(devOverdue)
                // Incident
                .incidentOpen(incDraft).incidentInProgress(incPending)
                .incidentClosed(incClosed).incidentOverdue(incOverdue)
                .incidentCriticalSeverity(incCritical)
                // Change Control
                .changeControlOpen(ccDraft).changeControlPendingApproval(ccPending)
                .changeControlClosed(ccClosed).changeControlOverdue(ccOverdue)
                // Complaint
                .complaintOpen(mcDraft).complaintInProgress(mcPending)
                .complaintClosed(mcClosed).complaintOverdue(mcOverdue)
                .complaintReportableToAuthority(mcReportable)
                // Aggregates
                .totalOpenRecords(totalDraft + totalPending)
                .totalOverdueRecords(totalOverdue)
                .statusBreakdown(statusBreakdown)
                .priorityBreakdown(priorityBreakdown)
                .build();
    }
}
