package com.qms.module.audit.entity;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit log record.
 *
 * Design decisions:
 * ─────────────────
 * 1. No BaseEntity extension — audit logs must NEVER be soft-deleted, updated,
 *    or tracked by Spring JPA Auditing (that would create recursive audit events).
 *    created_at is set in @PrePersist and is the single source of truth for timing.
 *
 * 2. No FK constraint to users.id — audit logs must survive even if a user is
 *    purged from the system. user_id is a plain BIGINT, not a foreign key.
 *
 * 3. old_value and new_value are TEXT (JSON strings). Sensitive fields
 *    (passwords, tokens) are masked before storage by AuditValueSerializer.
 *
 * 4. The table is append-only. Application code must NEVER call update or delete
 *    on this table. Archival moves old rows to audit_logs_archive, never removes them.
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_al_user_id",     columnList = "user_id"),
        @Index(name = "idx_al_action",      columnList = "action"),
        @Index(name = "idx_al_module",      columnList = "module"),
        @Index(name = "idx_al_entity",      columnList = "entity_type, entity_id"),
        @Index(name = "idx_al_timestamp",   columnList = "\"timestamp\""),
        @Index(name = "idx_al_outcome",     columnList = "outcome"),
        @Index(name = "idx_al_session_id",  columnList = "session_id"),
        @Index(name = "idx_al_correlation", columnList = "correlation_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    // ── Identity ────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    // ── Who ────────────────────────────────────────────────

    /** Database ID of the acting user — NOT a foreign key (see design decisions). */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "user_full_name", length = 160)
    private String userFullName;

    @Column(name = "user_department", length = 100)
    private String userDepartment;

    // ── What ───────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 50)
    private AuditModule module;

    /** Java simple class name of the affected entity, e.g. "User", "Capa", "Document". */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /** Primary key of the affected entity. */
    @Column(name = "entity_id")
    private Long entityId;

    /** Human-readable description, e.g. "User john.doe was deactivated". */
    @Column(name = "description", length = 500)
    private String description;

    // ── Before / After ─────────────────────────────────────

    /** JSON snapshot of the entity BEFORE the operation (null for CREATE). */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** JSON snapshot of the entity AFTER the operation (null for DELETE). */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    // ── Outcome ────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    @Builder.Default
    private AuditOutcome outcome = AuditOutcome.SUCCESS;

    /** Exception class name if outcome == FAILURE. */
    @Column(name = "error_type", length = 200)
    private String errorType;

    /** Exception message if outcome == FAILURE (truncated to 1000 chars). */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // ── Network / Request context ───────────────────────────

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** HTTP method + URI path, e.g. "POST /api/v1/capa". */
    @Column(name = "request_uri", length = 300)
    private String requestUri;

    /** Correlation ID from X-Correlation-ID header for request tracing. */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /** JWT session identifier (jti claim) for grouping all events in a user session. */
    @Column(name = "session_id", length = 100)
    private String sessionId;

    // ── Timing ─────────────────────────────────────────────

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    /** How long the audited operation took in milliseconds. */
    @Column(name = "duration_ms")
    private Long durationMs;

    // ── Lifecycle ──────────────────────────────────────────

    @PrePersist
    protected void onPersist() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
