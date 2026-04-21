package com.qms.module.audit.aop;

import com.qms.common.enums.AuditOutcome;
import com.qms.module.audit.annotation.Audited;
import com.qms.module.audit.context.AuditContextHolder;
import com.qms.module.audit.entity.AuditLog;
import com.qms.module.audit.service.AuditLogService;
import com.qms.module.audit.service.AuditValueSerializer;
import com.qms.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP around-advice for {@link Audited}-annotated service methods.
 *
 * Execution order:
 * ──────────────
 * 1. Extract user context from SecurityContextHolder
 * 2. Capture old value (if captureOldValue = true)
 * 3. Start timer
 * 4. Execute the target method (joinPoint.proceed())
 * 5. Capture new value from return value (if captureNewValue = true)
 * 6. Extract entity ID
 * 7. Persist audit log asynchronously via AuditLogService
 *
 * On exception:
 * ─────────────
 * If logOnFailure = true (default), an audit entry with outcome = FAILURE is
 * written regardless. The exception is re-thrown so Spring's normal exception
 * handling is not disrupted.
 *
 * Thread safety:
 * ─────────────
 * The aspect is stateless; all state lives on the call stack or in the
 * AuditContextHolder ThreadLocal. It is safe for concurrent use.
 */
@Slf4j
@Aspect
@Component
@Order(10)  // run after security/transaction advisors (lower = earlier)
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService      auditLogService;
    private final AuditValueSerializer valueSerializer;

    /**
     * Intercept any method annotated with @Audited anywhere in the Spring context.
     */
    @Around("@annotation(audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {

        long startMs = System.currentTimeMillis();
        Object[] args = joinPoint.getArgs();

        // ── 1. Capture old value before execution ────────────
        String oldValueJson = null;
        if (audited.captureOldValue() && args.length > 0) {
            oldValueJson = valueSerializer.serializeIfPresent(args[0]);
        }

        Object result     = null;
        Throwable caught  = null;

        // ── 2. Execute the target method ─────────────────────
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            caught = ex;
        }

        // ── 3. Build and persist the audit log ───────────────
        try {
            long durationMs = System.currentTimeMillis() - startMs;

            AuditLog entry = buildEntry(audited, joinPoint, args,
                    result, caught, oldValueJson, durationMs);

            auditLogService.log(entry);

        } catch (Exception loggingEx) {
            // Never let audit logging crash the business method
            log.error("AuditAspect failed to log: {}", loggingEx.getMessage(), loggingEx);
        } finally {
            AuditContextHolder.clear();
        }

        // ── 4. Re-throw if the target method threw ───────────
        if (caught != null) throw caught;
        return result;
    }

    // ─── Entry construction ───────────────────────────────────

    private AuditLog buildEntry(Audited audited,
                                 ProceedingJoinPoint joinPoint,
                                 Object[] args,
                                 Object result,
                                 Throwable caught,
                                 String oldValueJson,
                                 long durationMs) {

        // Determine outcome
        AuditOutcome outcome = (caught != null) ? AuditOutcome.FAILURE : AuditOutcome.SUCCESS;

        // Skip logging on failure if not configured
        if (caught != null && !audited.logOnFailure()) {
            return null; // caller ignores null returns
        }

        // Entity type — annotation > context > return type inference
        String entityType = resolveEntityType(audited, result);

        // Entity ID — annotation > context > return value getId()
        Long entityId = resolveEntityId(audited, args, result);

        // Description
        String description = resolveDescription(audited, entityType, entityId);

        // New value (capture return value unless suppressed or failed)
        String newValueJson = null;
        if (audited.captureNewValue() && result != null && caught == null) {
            newValueJson = valueSerializer.serialize(result);
        }

        // Merge thread-local context (if set programmatically during method execution)
        var ctx = AuditContextHolder.get();
        if (ctx != null) {
            if (ctx.getEntityId()    != null) entityId    = ctx.getEntityId();
            if (ctx.getEntityType()  != null) entityType  = ctx.getEntityType();
            if (ctx.getDescription() != null) description = ctx.getDescription();
            // Old value set by the service before it modifies the entity
            if (ctx.getOldValue() != null) oldValueJson = ctx.getOldValue();
            if (newValueJson == null && ctx.getAdditionalData() != null) {
                newValueJson = valueSerializer.serialize(ctx.getAdditionalData());
            }
        }

        // Build the entity
        var builder = AuditLog.builder()
                .action(audited.action())
                .module(audited.module())
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .oldValue(oldValueJson)
                .newValue(newValueJson)
                .outcome(outcome)
                .durationMs(durationMs);

        // Populate error fields
        if (caught != null) {
            builder.errorType(caught.getClass().getName())
                   .errorMessage(truncate(caught.getMessage(), 1000));
        }

        // Populate security context (must happen on calling HTTP thread, not async)
        SecurityUtils.getCurrentPrincipal().ifPresent(p -> {
            builder.userId(p.getId())
                   .username(p.getUsername())
                   .userFullName(p.getFullName())
                   .sessionId(p.getSessionId());
        });

        // Populate request context (must happen on calling HTTP thread before going async)
        enrichFromCurrentRequest(builder);

        return builder.build();
    }

    // ─── Request context capture ──────────────────────────────

    private void enrichFromCurrentRequest(AuditLog.AuditLogBuilder builder) {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest req = attrs.getRequest();

            String forwarded = req.getHeader("X-Forwarded-For");
            String ip = (forwarded != null && !forwarded.isBlank())
                    ? forwarded.split(",")[forwarded.split(",").length - 1].trim()
                    : req.getRemoteAddr();

            builder.ipAddress(ip)
                   .userAgent(req.getHeader("User-Agent"))
                   .requestUri(req.getMethod() + " " + req.getRequestURI())
                   .correlationId(req.getHeader("X-Correlation-ID"));
        } catch (IllegalStateException ignored) {
            // No HTTP request context — background thread or scheduled task
        }
    }

    // ─── Resolution helpers ───────────────────────────────────

    private String resolveEntityType(Audited audited, Object result) {
        // 1. Explicit annotation value
        if (!audited.entityType().isBlank()) return audited.entityType();

        // 2. Thread-local context
        var ctx = AuditContextHolder.get();
        if (ctx != null && ctx.getEntityType() != null) return ctx.getEntityType();

        // 3. Return value class simple name
        if (result != null) return result.getClass().getSimpleName();

        return null;
    }

    private Long resolveEntityId(Audited audited, Object[] args, Object result) {
        // 1. Explicit argument index
        int idx = audited.entityIdArgIndex();
        if (idx >= 0 && idx < args.length && args[idx] instanceof Long id) {
            return id;
        }

        // 2. Thread-local context
        var ctx = AuditContextHolder.get();
        if (ctx != null && ctx.getEntityId() != null) return ctx.getEntityId();

        // 3. Try getId() on the return value
        return extractId(result);
    }

    private String resolveDescription(Audited audited, String entityType, Long entityId) {
        if (!audited.description().isBlank()) return audited.description();

        var ctx = AuditContextHolder.get();
        if (ctx != null && ctx.getDescription() != null) return ctx.getDescription();

        // Auto-generate: "CREATE on User:42"
        StringBuilder sb = new StringBuilder(audited.action().name());
        if (entityType != null) {
            sb.append(" on ").append(entityType);
            if (entityId != null) sb.append(":").append(entityId);
        }
        return sb.toString();
    }

    private Long extractId(Object obj) {
        if (obj == null) return null;
        try {
            var method = obj.getClass().getMethod("getId");
            Object id  = method.invoke(obj);
            if (id instanceof Long  l) return l;
            if (id instanceof Integer i) return i.longValue();
        } catch (Exception ignored) {}
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
