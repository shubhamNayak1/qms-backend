package com.qms.module.audit.interceptor;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import com.qms.module.audit.entity.AuditLog;
import com.qms.module.audit.service.AuditLogService;
import com.qms.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP interceptor that tracks every inbound REST request.
 *
 * Responsibilities:
 *  1. Stamps a start time on the request for duration measurement.
 *  2. After handler execution, logs sensitive HTTP events:
 *       - 401 Unauthorized → LOGIN_FAILED (no authenticated principal)
 *       - 403 Forbidden    → DENIED
 *       - 5xx Server Error → FAILURE
 *
 * This interceptor does NOT log every GET/POST — that would be too noisy.
 * Non-sensitive reads are covered by the @Audited AOP aspect on service methods.
 * For full HTTP audit trails (all requests), see AuditRequestLoggingFilter.
 *
 * Register in WebMvcConfig:
 * <pre>
 *   registry.addInterceptor(auditHttpInterceptor)
 *           .addPathPatterns("/api/**");
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditHttpInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "audit_start_ms";

    private final AuditLogService auditLogService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest  request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                          Exception ex) {

        int status = response.getStatus();
        long startMs = getAttribute(request, START_TIME_ATTR);
        long durationMs = System.currentTimeMillis() - startMs;

        // Only log security-relevant HTTP outcomes to avoid log flooding
        if (status == 401) {
            persistSecurityEvent(request, AuditAction.LOGIN_FAILED,
                    AuditOutcome.DENIED, "Unauthorized access to " + request.getRequestURI(), durationMs);

        } else if (status == 403) {
            persistSecurityEvent(request, AuditAction.LOGIN_FAILED,
                    AuditOutcome.DENIED, "Forbidden: " + request.getRequestURI(), durationMs);

        } else if (status >= 500 && ex != null) {
            persistSecurityEvent(request, AuditAction.SYSTEM_EVENT,
                    AuditOutcome.FAILURE, "Server error on " + request.getMethod()
                            + " " + request.getRequestURI() + ": " + ex.getMessage(), durationMs);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────

    private void persistSecurityEvent(HttpServletRequest request,
                                       AuditAction action,
                                       AuditOutcome outcome,
                                       String description,
                                       long durationMs) {
        var builder = AuditLog.builder()
                .action(action)
                .module(AuditModule.SYSTEM)
                .outcome(outcome)
                .description(description)
                .ipAddress(extractIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .requestUri(request.getMethod() + " " + request.getRequestURI())
                .correlationId(request.getHeader("X-Correlation-ID"))
                .durationMs(durationMs);

        // Enrich with authenticated principal if available
        SecurityUtils.getCurrentPrincipal().ifPresent(p -> {
            builder.userId(p.getId())
                   .username(p.getUsername())
                   .userFullName(p.getFullName())
                   .sessionId(p.getSessionId());
        });

        auditLogService.log(builder.build());
    }

    private String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
    }

    private long getAttribute(HttpServletRequest req, String key) {
        Object val = req.getAttribute(key);
        return val instanceof Long l ? l : System.currentTimeMillis();
    }
}
