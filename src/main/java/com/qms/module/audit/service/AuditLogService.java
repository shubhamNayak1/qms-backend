package com.qms.module.audit.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import com.qms.common.exception.AppException;
import com.qms.common.response.PageResponse;
import com.qms.module.audit.dto.request.AuditSearchRequest;
import com.qms.module.audit.dto.request.ManualAuditRequest;
import com.qms.module.audit.dto.response.AuditLogResponse;
import com.qms.module.audit.dto.response.AuditStatsResponse;
import com.qms.module.audit.entity.AuditLog;
import com.qms.module.audit.repository.AuditLogRepository;
import com.qms.module.audit.repository.AuditLogSpecification;
import com.qms.module.user.entity.Role;
import com.qms.module.user.repository.RoleSpecification;
import com.qms.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String CORRELATION_ID = "X-Correlation-ID";

    private final AuditLogRepository   auditLogRepository;
    private final AuditValueSerializer valueSerializer;

    // ─── Core write ───────────────────────────────────────────

    /**
     * Primary audit write method. Called by {@link com.qms.module.audit.aop.AuditAspect}
     * and the {@link com.qms.module.audit.interceptor.AuditHttpInterceptor}.
     *
     * Runs in a NEW transaction so audit entries are persisted even if the
     * calling transaction rolls back (e.g. we still want to record a FAILURE outcome).
     */
    @Async("auditTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditLog auditLog) {
        try {
            enrichWithRequestContext(auditLog);
            enrichWithSecurityContext(auditLog);
            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: action={} module={} entity={}:{}",
                    auditLog.getAction(), auditLog.getModule(),
                    auditLog.getEntityType(), auditLog.getEntityId());
        } catch (Exception e) {
            // Audit logging must NEVER crash the application — log the failure and move on
            log.error("CRITICAL: Failed to persist audit log — action={} module={}: {}",
                    auditLog.getAction(), auditLog.getModule(), e.getMessage());
        }
    }

    /**
     * Fluent builder entry point — preferred for programmatic audit logging.
     *
     * <pre>
     *   auditLogService.record()
     *       .action(AuditAction.APPROVE)
     *       .module(AuditModule.DOCUMENT)
     *       .entityType("Document").entityId(docId)
     *       .description("Document approved by QA Manager")
     *       .newValue(documentDto)
     *       .save();
     * </pre>
     */
    public AuditBuilder record() {
        return new AuditBuilder(this, valueSerializer);
    }

    /**
     * Convenience: log a simple action with no entity context.
     * Useful for login/logout events.
     */
    @Async("auditTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(AuditAction action, AuditModule module, String description) {
        log(AuditLog.builder()
                .action(action)
                .module(module)
                .description(description)
                .outcome(AuditOutcome.SUCCESS)
                .build());
    }

    /**
     * Manual audit log from a REST endpoint — used when automatic AOP capture
     * is not possible (e.g. external system events, batch operations).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLogResponse logManual(ManualAuditRequest req) {
        AuditLog entry = AuditLog.builder()
                .action(req.getAction())
                .module(req.getModule())
                .entityType(req.getEntityType())
                .entityId(req.getEntityId())
                .description(req.getDescription())
                .oldValue(req.getOldValue())
                .newValue(req.getNewValue())
                .outcome(req.getOutcome() != null ? req.getOutcome() : AuditOutcome.SUCCESS)
                .correlationId(req.getCorrelationId())
                .build();
        enrichWithRequestContext(entry);
        enrichWithSecurityContext(entry);
        return toResponse(auditLogRepository.save(entry));
    }

    // ─── Queries ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditSearchRequest req) {
        Specification<AuditLog> spec = AuditLogSpecification.filter(req.getUserId(),req.getUsername(),req.getAction(),req.getModule(),req.getEntityType(),req.getEntityId(),req.getOutcome(),req.getIpAddress(),req.getFrom(),req.getTo());
        var page = auditLogRepository.findAll(spec,
                PageRequest.of(req.getPage(), req.getSize(), Sort.by("timestamp").descending()));

//        var page = auditLogRepository.search(
//                req.getUserId(), req.getUsername(),
//                req.getAction(), req.getModule(),
//                req.getEntityType(), req.getEntityId(),
//                req.getOutcome(), req.getIpAddress(),
//                req.getFrom(), req.getTo(),
//                PageRequest.of(req.getPage(), req.getSize(),
//                        Sort.by("timestamp").descending())
//        );
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getById(Long id) {
        return auditLogRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> AppException.notFound("AuditLog", id));
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getByEntity(String entityType, Long entityId) {
        return auditLogRepository
                .findByEntityTypeAndEntityId(entityType, entityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getSessionTrail(String sessionId) {
        return auditLogRepository
                .findBySessionIdOrderByTimestampAsc(sessionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AuditStatsResponse getStats(LocalDateTime since) {
        if (since == null) since = LocalDateTime.now().minusDays(30);

        long totalEvents   = auditLogRepository.countByOutcomeSince(AuditOutcome.SUCCESS, since)
                           + auditLogRepository.countByOutcomeSince(AuditOutcome.FAILURE, since);
        long failures      = auditLogRepository.countByOutcomeSince(AuditOutcome.FAILURE, since);
        long loginEvents   = auditLogRepository.countByActionSince(AuditAction.LOGIN, since);
        long loginFailures = auditLogRepository.countByActionSince(AuditAction.LOGIN_FAILED, since);

        List<Object[]> byModule = auditLogRepository.countByModuleSince(since);
        Map<String, Long> moduleBreakdown = byModule.stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));

        return AuditStatsResponse.builder()
                .totalEvents(totalEvents)
                .successEvents(totalEvents - failures)
                .failureEvents(failures)
                .loginEvents(loginEvents)
                .loginFailures(loginFailures)
                .moduleBreakdown(moduleBreakdown)
                .periodFrom(since)
                .periodTo(LocalDateTime.now())
                .build();
    }

    // ─── Enrichment helpers ───────────────────────────────────

    private void enrichWithRequestContext(AuditLog entry) {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest req = attrs.getRequest();

            if (entry.getIpAddress() == null) {
                entry.setIpAddress(extractClientIp(req));
            }
            if (entry.getUserAgent()  == null) entry.setUserAgent(req.getHeader("User-Agent"));
            if (entry.getRequestUri() == null) entry.setRequestUri(req.getMethod() + " " + req.getRequestURI());
            if (entry.getCorrelationId() == null) entry.setCorrelationId(req.getHeader(CORRELATION_ID));

        } catch (IllegalStateException e) {
            // No HTTP request context — background thread or test
        }
    }

    /**
     * Extracts the client IP from the request.
     *
     * When behind a trusted reverse proxy, X-Forwarded-For contains a comma-separated
     * list where the leftmost entry is the original client and later entries are added
     * by each proxy. We take the LAST entry in the chain — this is the IP of the
     * proxy/load-balancer that talked directly to the application, which cannot be
     * forged by the end client. If there is no X-Forwarded-For header (direct connection),
     * we fall back to getRemoteAddr().
     *
     * If your infrastructure uses a single trusted proxy, taking the last entry is safe.
     * Adjust the index if your deployment has a different proxy topology.
     */
    private String extractClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            // Rightmost entry is the IP added by the trusted proxy — cannot be forged by clients
            return parts[parts.length - 1].trim();
        }
        return req.getRemoteAddr();
    }

    private void enrichWithSecurityContext(AuditLog entry) {
        if (entry.getUserId() != null) return; // already set by caller

        SecurityUtils.getCurrentPrincipal().ifPresent(principal -> {
            if (entry.getUserId()      == null) entry.setUserId(principal.getId());
            if (entry.getUsername()    == null) entry.setUsername(principal.getUsername());
            if (entry.getUserFullName() == null) entry.setUserFullName(principal.getFullName());
            if (entry.getSessionId()   == null) entry.setSessionId(principal.getSessionId());
        });
    }

    // ─── Mapping ─────────────────────────────────────────────

    AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .username(log.getUsername())
                .userFullName(log.getUserFullName())
                .userDepartment(log.getUserDepartment())
                .action(log.getAction())
                .module(log.getModule())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .description(log.getDescription())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .outcome(log.getOutcome())
                .errorType(log.getErrorType())
                .errorMessage(log.getErrorMessage())
                .ipAddress(log.getIpAddress())
                .requestUri(log.getRequestUri())
                .correlationId(log.getCorrelationId())
                .sessionId(log.getSessionId())
                .timestamp(log.getTimestamp())
                .durationMs(log.getDurationMs())
                .build();
    }
}
