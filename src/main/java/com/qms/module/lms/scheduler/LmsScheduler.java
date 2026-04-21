package com.qms.module.lms.scheduler;

import com.qms.module.lms.entity.Enrollment;
import com.qms.module.lms.repository.EnrollmentRepository;
import com.qms.module.lms.service.CertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled jobs for LMS lifecycle management.
 *
 * Jobs:
 *  1. Overdue detection (daily 07:00) — flags enrollments past due date
 *  2. Certificate expiry (daily 08:00) — expires lapsed certificates and their enrollments
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LmsScheduler {

    private final EnrollmentRepository enrollmentRepository;
    private final CertificateService   certificateService;

    @Value("${lms.training.overdue-warning-days:7}")
    private int warningDays;

    // ─────────────────────────────────────────────────────────
    // 1. Overdue enrollment detection
    // ─────────────────────────────────────────────────────────

    @Scheduled(cron = "${lms.scheduler.overdue-cron:0 0 7 * * *}")
    @Transactional
    public void detectOverdueEnrollments() {
        LocalDate today       = LocalDate.now();
        LocalDate warningDate = today.plusDays(warningDays);

        List<Enrollment> overdue = enrollmentRepository.findOverdue(today);
        if (!overdue.isEmpty()) {
            log.warn("LMS OVERDUE: {} enrollment(s) are past their due date:", overdue.size());
            overdue.forEach(e -> log.warn("  → enrollmentId={} userId={} program={} dueDate={}",
                    e.getId(), e.getUserId(),
                    e.getProgram() != null ? e.getProgram().getCode() : "N/A",
                    e.getDueDate()));
            // TODO: fire OverdueEnrollmentEvent → notification service / email
        }

        List<Enrollment> dueSoon = enrollmentRepository.findDueSoon(today, warningDate);
        if (!dueSoon.isEmpty()) {
            log.info("LMS DUE SOON: {} enrollment(s) are due within {} days:",
                    dueSoon.size(), warningDays);
            dueSoon.forEach(e -> log.info("  → enrollmentId={} userId={} program={} dueDate={}",
                    e.getId(), e.getUserId(),
                    e.getProgram() != null ? e.getProgram().getCode() : "N/A",
                    e.getDueDate()));
            // TODO: fire DueSoonEnrollmentEvent → reminder email service
        }

        log.info("LMS overdue check done — overdue={} dueSoon={}", overdue.size(), dueSoon.size());
    }

    // ─────────────────────────────────────────────────────────
    // 2. Certificate & enrollment expiry
    // ─────────────────────────────────────────────────────────

    @Scheduled(cron = "${lms.scheduler.expiry-cron:0 0 8 * * *}")
    public void expireLapsedCertificates() {
        certificateService.expireLapsed();
    }
}
