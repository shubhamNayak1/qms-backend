package com.qms.module.lms.service;

import com.qms.module.lms.dto.response.ComplianceDashboardResponse;
import com.qms.module.lms.entity.Enrollment;
import com.qms.module.lms.enums.CertificateStatus;
import com.qms.module.lms.enums.EnrollmentStatus;
import com.qms.module.lms.enums.ProgramStatus;
import com.qms.module.lms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LmsComplianceService {

    private final EnrollmentRepository          enrollmentRepository;
    private final TrainingProgramRepository     programRepository;
    private final TrainingCertificateRepository certificateRepository;
    private final EnrollmentService             enrollmentService;
    private final CertificateService            certificateService;

    @Value("${lms.training.overdue-warning-days:7}")
    private int overdueWarningDays;

    public ComplianceDashboardResponse getDashboard() {
        LocalDate today       = LocalDate.now();
        LocalDate warningDate = today.plusDays(overdueWarningDays);

        // ── Enrollment counts ─────────────────────────────────
        // Use countByIsDeletedFalse to exclude soft-deleted rows
        long total      = enrollmentRepository.countByIsDeletedFalse();
        // Bug fix: new enrollments use ALLOCATED, not the legacy ENROLLED status.
        // Count both together so old and new data are both represented.
        long allocated  = enrollmentRepository.countByStatusAndIsDeletedFalse(EnrollmentStatus.ALLOCATED)
                        + enrollmentRepository.countByStatusAndIsDeletedFalse(EnrollmentStatus.ENROLLED);
        long inProgress = enrollmentRepository.countByStatusAndIsDeletedFalse(EnrollmentStatus.IN_PROGRESS);
        long completed  = enrollmentRepository.countByStatusAndIsDeletedFalse(EnrollmentStatus.COMPLETED);
        long failed     = enrollmentRepository.countByStatusAndIsDeletedFalse(EnrollmentStatus.FAILED);
        long expired    = enrollmentRepository.countByStatusAndIsDeletedFalse(EnrollmentStatus.EXPIRED);
        long waived     = enrollmentRepository.countByStatusAndIsDeletedFalse(EnrollmentStatus.WAIVED);
        long cancelled  = enrollmentRepository.countByStatusAndIsDeletedFalse(EnrollmentStatus.CANCELLED);
        long retraining = enrollmentRepository.countByStatusAndIsDeletedFalse(EnrollmentStatus.RETRAINING);

        List<Enrollment> overdueList = enrollmentRepository.findOverdue(today);
        long overdueCount = overdueList.size();

        List<Enrollment> dueSoon = enrollmentRepository.findDueSoon(today, warningDate);

        // Compliance rate: (COMPLETED + WAIVED) / non-cancelled
        long nonCancelled = total - cancelled;
        double complianceRate = nonCancelled == 0 ? 0.0
                : Math.round((completed + waived) * 10000.0 / nonCancelled) / 100.0;

        // ── Program counts ────────────────────────────────────
        // Bug fix: count all non-deleted programs, not just ACTIVE+DRAFT
        long totalPrograms    = programRepository.countByIsDeletedFalse();
        long activePrograms   = programRepository.countByStatusAndIsDeletedFalse(ProgramStatus.ACTIVE);
        long mandatoryPrograms = programRepository.countByIsMandatoryTrueAndIsDeletedFalse();

        // ── Certificate counts ────────────────────────────────
        long activeCerts  = certificateRepository.findExpiringSoon(today, today.plusYears(10)).stream()
                .filter(c -> c.getStatus() == CertificateStatus.ACTIVE).count();
        long expiredCerts = certificateRepository.findLapsed(today).size();

        // ── Attention lists ───────────────────────────────────
        var expiringSoonCerts = certificateService.getExpiringSoon(overdueWarningDays);

        return ComplianceDashboardResponse.builder()
                .generatedAt(LocalDateTime.now())
                .totalEnrollments(total)
                .allocatedCount(allocated)
                .inProgressCount(inProgress)
                .completedCount(completed)
                .failedCount(failed)
                .expiredCount(expired)
                .waivedCount(waived)
                .cancelledCount(cancelled)
                .retrainingCount(retraining)
                .overdueCount(overdueCount)
                .overallComplianceRate(complianceRate)
                .totalPrograms(totalPrograms)
                .activePrograms(activePrograms)
                .mandatoryPrograms(mandatoryPrograms)
                .activeCertificates(activeCerts)
                .expiredCertificates(expiredCerts)
                .overdueEnrollments(overdueList.stream()
                        .map(enrollmentService::toResponse).toList())
                .dueSoonEnrollments(dueSoon.stream()
                        .map(enrollmentService::toResponse).toList())
                .expiringSoonCertificates(expiringSoonCerts)
                .build();
    }
}
