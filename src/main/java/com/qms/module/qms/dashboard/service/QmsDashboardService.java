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
 * All queries are read-only and run in a single transaction for
 * a consistent snapshot (no phantom reads between sub-modules).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QmsDashboardService {

    private final CapaRepository          capaRepository;
    private final DeviationRepository     deviationRepository;
    private final IncidentRepository      incidentRepository;
    private final ChangeControlRepository changeControlRepository;
    private final MarketComplaintRepository complaintRepository;

    public QmsDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();

        // ── CAPA ──────────────────────────────────────────────
        long capaOpen        = capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.OPEN);
        long capaInProgress  = capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.IN_PROGRESS);
        long capaPending     = capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_APPROVAL);
        long capaClosed      = capaRepository.countByStatusAndIsDeletedFalse(QmsStatus.CLOSED);
        long capaOverdue     = capaRepository.countOverdue(today);
        long capaCritical    = capaRepository.countByPriorityAndIsDeletedFalse(Priority.CRITICAL);

        // ── Deviation ─────────────────────────────────────────
        long devOpen         = deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.OPEN);
        long devInProgress   = deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.IN_PROGRESS);
        long devClosed       = deviationRepository.countByStatusAndIsDeletedFalse(QmsStatus.CLOSED);
        long devOverdue      = deviationRepository.countOverdue(today);

        // ── Incident ──────────────────────────────────────────
        long incOpen         = incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.OPEN);
        long incInProgress   = incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.IN_PROGRESS);
        long incClosed       = incidentRepository.countByStatusAndIsDeletedFalse(QmsStatus.CLOSED);
        long incOverdue      = incidentRepository.countOverdue(today);
        long incCritical     = incidentRepository.countBySeverityAndIsDeletedFalse("Critical");

        // ── Change Control ────────────────────────────────────
        long ccOpen          = changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.OPEN);
        long ccPending       = changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.PENDING_APPROVAL);
        long ccClosed        = changeControlRepository.countByStatusAndIsDeletedFalse(QmsStatus.CLOSED);
        long ccOverdue       = changeControlRepository.countOverdue(today);

        // ── Market Complaint ──────────────────────────────────
        long mcOpen          = complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.OPEN);
        long mcInProgress    = complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.IN_PROGRESS);
        long mcClosed        = complaintRepository.countByStatusAndIsDeletedFalse(QmsStatus.CLOSED);
        long mcOverdue       = complaintRepository.countOverdue(today);
        long mcReportable    = complaintRepository.countByReportableToAuthorityTrueAndIsDeletedFalse();

        // ── Cross-module aggregates ───────────────────────────
        long totalOpen   = capaOpen + devOpen + incOpen + ccOpen + mcOpen;
        long totalOverdue = capaOverdue + devOverdue + incOverdue + ccOverdue + mcOverdue;

        // Status breakdown across all modules
        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        statusBreakdown.put("OPEN",             capaOpen    + devOpen      + incOpen      + ccOpen    + mcOpen);
        statusBreakdown.put("IN_PROGRESS",      capaInProgress + devInProgress + incInProgress + 0L  + mcInProgress);
        statusBreakdown.put("PENDING_APPROVAL", capaPending + 0L           + 0L           + ccPending + 0L);
        statusBreakdown.put("CLOSED",           capaClosed  + devClosed    + incClosed    + ccClosed  + mcClosed);

        // Priority breakdown (CAPA only — most comprehensive priority tracking)
        Map<String, Long> priorityBreakdown = new LinkedHashMap<>();
        for (Priority p : Priority.values()) {
            priorityBreakdown.put(p.name(), capaRepository.countByPriorityAndIsDeletedFalse(p));
        }

        return QmsDashboardResponse.builder()
                .generatedAt(LocalDateTime.now())
                // CAPA
                .capaOpen(capaOpen).capaInProgress(capaInProgress)
                .capaPendingApproval(capaPending).capaClosed(capaClosed)
                .capaOverdue(capaOverdue).capaCritical(capaCritical)
                // Deviation
                .deviationOpen(devOpen).deviationInProgress(devInProgress)
                .deviationClosed(devClosed).deviationOverdue(devOverdue)
                // Incident
                .incidentOpen(incOpen).incidentInProgress(incInProgress)
                .incidentClosed(incClosed).incidentOverdue(incOverdue)
                .incidentCriticalSeverity(incCritical)
                // Change Control
                .changeControlOpen(ccOpen).changeControlPendingApproval(ccPending)
                .changeControlClosed(ccClosed).changeControlOverdue(ccOverdue)
                // Complaint
                .complaintOpen(mcOpen).complaintInProgress(mcInProgress)
                .complaintClosed(mcClosed).complaintOverdue(mcOverdue)
                .complaintReportableToAuthority(mcReportable)
                // Aggregates
                .totalOpenRecords(totalOpen)
                .totalOverdueRecords(totalOverdue)
                .statusBreakdown(statusBreakdown)
                .priorityBreakdown(priorityBreakdown)
                .build();
    }
}
