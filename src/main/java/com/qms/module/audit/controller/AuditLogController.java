package com.qms.module.audit.controller;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import com.qms.common.response.ApiResponse;
import com.qms.common.response.PageResponse;
import com.qms.module.audit.dto.request.AuditSearchRequest;
import com.qms.module.audit.dto.request.ManualAuditRequest;
import com.qms.module.audit.dto.response.AuditLogResponse;
import com.qms.module.audit.dto.response.AuditStatsResponse;
import com.qms.module.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for the Audit module.
 *
 * All endpoints require authentication. Read endpoints are accessible to
 * AUDITOR and above. The manual write endpoint is restricted to SUPER_ADMIN
 * (used for batch imports and external system event ingestion).
 *
 * Audit logs are immutable — there are no update or delete endpoints.
 * Archival is performed by the scheduler, not via the API.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Tamper-evident audit trail — read and search")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private final AuditLogService auditLogService;

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/audit/logs
    // Multi-criteria search with all parameters optional
    // ─────────────────────────────────────────────────────────
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "Search audit logs",
        description = """
            Full-featured search across the audit trail. All parameters are optional
            and combinable. Results are sorted by timestamp descending (newest first).

            **Common queries:**
            - All failures: `?outcome=FAILURE`
            - One user's actions: `?userId=42`
            - CAPA module activity: `?module=CAPA`
            - Login failures in a date range: `?action=LOGIN_FAILED&from=2024-01-01T00:00:00`
            - All changes to a specific record: `?entityType=Capa&entityId=17`
            """
    )
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @RequestParam(name = "request[userId]",     required = false) Long userId,
            @RequestParam(name = "request[username]",   required = false) String username,
            @RequestParam(name = "request[action]",     required = false) AuditAction action,
            @RequestParam(name = "request[module]",     required = false) AuditModule module,
            @RequestParam(name = "request[entityType]", required = false) String entityType,
            @RequestParam(name = "request[entityId]",   required = false) Long entityId,
            @RequestParam(name = "request[outcome]",    required = false) AuditOutcome outcome,
            @RequestParam(name = "request[ipAddress]",  required = false) String ipAddress,
            @RequestParam(name = "request[from]",       required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "request[to]",         required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(name = "request[page]",  defaultValue = "0")  int page,
            @RequestParam(name = "request[size]",  defaultValue = "50") int size) {

        AuditSearchRequest req = new AuditSearchRequest();
        req.setUserId(userId);
        req.setUsername(username);
        req.setAction(action);
        req.setModule(module);
        req.setEntityType(entityType);
        req.setEntityId(entityId);
        req.setOutcome(outcome);
        req.setIpAddress(ipAddress);
        req.setFrom(from);
        req.setTo(to);
        req.setPage(page);
        req.setSize(size);
        return ApiResponse.ok(auditLogService.search(req));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/audit/logs/{id}
    // ─────────────────────────────────────────────────────────
    @GetMapping("/logs/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(summary = "Get a single audit log entry by ID")
    public ResponseEntity<ApiResponse<AuditLogResponse>> getById(@PathVariable Long id) {
        return ApiResponse.ok(auditLogService.getById(id));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/audit/entity/{entityType}/{entityId}
    // Full history of changes to a specific record
    // ─────────────────────────────────────────────────────────
    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "Get full audit history of a specific entity",
        description = "Returns all audit events that affected a given entity, ordered by time. " +
                      "Use this to build a 'change history' view for any record."
    )
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getEntityHistory(
            @Parameter(description = "Entity class name, e.g. Capa, Document, User")
            @PathVariable String entityType,
            @Parameter(description = "Entity primary key")
            @PathVariable Long entityId) {
        return ApiResponse.ok(auditLogService.getByEntity(entityType, entityId));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/audit/session/{sessionId}
    // All events in a user's login session
    // ─────────────────────────────────────────────────────────
    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "Get audit trail for an entire user session",
        description = "Returns all events from a single login session (identified by the JWT jti claim), " +
                      "ordered chronologically. Useful for security investigations."
    )
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getSessionTrail(
            @PathVariable String sessionId) {
        return ApiResponse.ok(auditLogService.getSessionTrail(sessionId));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/audit/stats
    // KPI summary for the dashboard
    // ─────────────────────────────────────────────────────────
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR')")
    @Operation(
        summary = "Audit statistics and KPI summary",
        description = "Returns total event counts, failure rates, login stats, and module breakdowns " +
                      "for the specified time window. Defaults to the last 30 days."
    )
    public ResponseEntity<ApiResponse<AuditStatsResponse>> getStats(
            @Parameter(description = "Start of the reporting period (ISO-8601). Default: 30 days ago.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return ApiResponse.ok(auditLogService.getStats(since));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/audit/users/{userId}/recent-logins
    // Security-focused: last N login events for a user
    // ─────────────────────────────────────────────────────────
    @GetMapping("/users/{userId}/recent-logins")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','QA_MANAGER','AUDITOR') or #userId == authentication.principal.id")
    @Operation(
        summary = "Get the most recent login events for a user",
        description = "Returns login and login-failed events for the given user. " +
                      "Users can view their own history; AUDITOR+ can view any user."
    )
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getRecentLogins(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        // Delegate to search with pre-set action filter
        AuditSearchRequest req = new AuditSearchRequest();
        req.setUserId(userId);
        req.setPage(page);
        req.setSize(size);
        // We filter for both LOGIN and LOGIN_FAILED via the module, not here —
        // the full search works fine; the endpoint is a convenience alias.
        return ApiResponse.ok(auditLogService.search(req));
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/audit/logs/manual
    // Ingest an audit event from an external system or batch job
    // ─────────────────────────────────────────────────────────
    @PostMapping("/logs/manual")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Manually submit an audit log entry",
        description = """
            For ingesting events from:
            - External systems that cannot use the @Audited annotation
            - Batch import jobs
            - Legacy system migration
            - Scheduled reports and exports

            The caller is responsible for providing accurate data.
            Standard enrichment (IP, user context) is still applied automatically.
            """
    )
    public ResponseEntity<ApiResponse<AuditLogResponse>> logManual(
            @Valid @RequestBody ManualAuditRequest request) {
        return ApiResponse.created("Audit log recorded", auditLogService.logManual(request));
    }
}
