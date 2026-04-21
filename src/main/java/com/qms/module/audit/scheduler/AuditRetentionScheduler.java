package com.qms.module.audit.scheduler;

import com.qms.module.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled archival job for old audit log entries.
 *
 * Strategy:
 * ─────────
 * 1. Every Sunday at 02:00 (configurable via audit.retention.cron), find
 *    all log IDs older than the retention threshold (default 365 days).
 * 2. Delete them in batches of 1000 to avoid lock contention and transaction timeouts.
 * 3. In production, replace the DELETE with an INSERT INTO audit_logs_archive +
 *    DELETE FROM audit_logs to preserve the data in cold storage.
 *
 * The job is disabled unless audit.retention.enabled = true, so it has zero
 * impact on dev/test environments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audit.retention.enabled", havingValue = "true")
public class AuditRetentionScheduler {

    private static final int BATCH_SIZE = 1000;

    private final AuditLogRepository auditLogRepository;

    @Value("${audit.retention.days:365}")
    private int retentionDays;

    /**
     * Main archival job.
     * Cron default: every Sunday at 02:00 — low-traffic period.
     */
    @Scheduled(cron = "${audit.retention.cron:0 0 2 * * SUN}")
    public void archiveOldLogs() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        log.info("Audit retention job started — archiving logs before {}", threshold);

        long totalDeleted = 0;
        List<Long> batch;

        do {
            batch = auditLogRepository.findIdsBefore(
                    threshold, PageRequest.of(0, BATCH_SIZE));

            if (!batch.isEmpty()) {
                deleteBatch(batch);
                totalDeleted += batch.size();
                log.debug("Archived {} audit log(s) this batch (total so far: {})",
                        batch.size(), totalDeleted);
            }

        } while (batch.size() == BATCH_SIZE); // keep going until no more remain

        log.info("Audit retention job completed — {} log(s) archived", totalDeleted);
    }

    /**
     * Deletes a batch of logs in its own transaction.
     * A separate transaction per batch means:
     *   - Each batch commits independently (no single huge transaction)
     *   - A failure in one batch does not roll back already-archived batches
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        auditLogRepository.deleteByIds(ids);
    }
}
