package com.qms.module.dms.scheduler;

import com.qms.module.dms.entity.Document;
import com.qms.module.dms.enums.DocumentStatus;
import com.qms.module.dms.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled tasks for document lifecycle management.
 *
 * Runs daily at 08:00 (configurable via dms.expiry.check-cron).
 *
 * Tasks:
 * 1. Detect documents expiring within the warning window → log warnings
 *    (in production, fire notification events / emails here)
 * 2. Automatically OBSOLETE documents that are past their expiry date
 *    (configurable — some organisations prefer manual obsoleting)
 * 3. Detect documents due for periodic review
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentExpiryScheduler {

    private final DocumentRepository documentRepository;

    @Value("${dms.expiry.warning-days:30}")
    private int warningDays;

    @Scheduled(cron = "${dms.expiry.check-cron:0 0 8 * * *}")
    @Transactional
    public void checkDocumentExpiry() {
        LocalDate today       = LocalDate.now();
        LocalDate warningDate = today.plusDays(warningDays);

        // ── 1. Expiring-soon warnings ──────────────────────────
        List<Document> expiringSoon = documentRepository.findExpiringSoon(today, warningDate);
        if (!expiringSoon.isEmpty()) {
            log.warn("DMS EXPIRY WARNING: {} document(s) expire within {} days:",
                    expiringSoon.size(), warningDays);
            expiringSoon.forEach(doc ->
                    log.warn("  → {} v{} '{}' expires on {}",
                            doc.getDocNumber(), doc.getVersion(),
                            doc.getTitle(), doc.getExpiryDate()));
            // TODO: fire DocumentExpiringEvent → email service / notification service
        }

        // ── 2. Auto-obsolete expired documents ─────────────────
        List<Document> expired = documentRepository.findExpired(today);
        if (!expired.isEmpty()) {
            log.warn("DMS EXPIRY: {} document(s) have expired and will be auto-obsoleted:",
                    expired.size());
            expired.forEach(doc -> {
                log.warn("  → {} v{} '{}' expired on {}",
                        doc.getDocNumber(), doc.getVersion(),
                        doc.getTitle(), doc.getExpiryDate());
                doc.setStatus(DocumentStatus.OBSOLETE);
                doc.setObsoletedAt(java.time.LocalDateTime.now());
                doc.setApprovalComments("Auto-obsoleted by system: expiry date reached");
            });
            documentRepository.saveAll(expired);
        }

        // ── 3. Review due alerts ───────────────────────────────
        List<Document> dueForReview = documentRepository.findDueForReview(today, warningDate);
        if (!dueForReview.isEmpty()) {
            log.info("DMS REVIEW: {} document(s) are due for periodic review within {} days:",
                    dueForReview.size(), warningDays);
            dueForReview.forEach(doc ->
                    log.info("  → {} v{} '{}' review due on {}",
                            doc.getDocNumber(), doc.getVersion(),
                            doc.getTitle(), doc.getReviewDate()));
            // TODO: fire DocumentReviewDueEvent → notification service
        }

        log.info("DMS expiry check complete — expiringSoon={} autoObsoleted={} reviewDue={}",
                expiringSoon.size(), expired.size(), dueForReview.size());
    }
}
