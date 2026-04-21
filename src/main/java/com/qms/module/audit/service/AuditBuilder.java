package com.qms.module.audit.service;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;
import com.qms.common.enums.AuditOutcome;
import com.qms.module.audit.entity.AuditLog;
import lombok.RequiredArgsConstructor;

/**
 * Fluent builder for constructing and persisting {@link AuditLog} entries
 * without touching the entity directly.
 *
 * Designed to be retrieved via {@link AuditLogService#record()} and always
 * terminated with {@link #save()}.
 *
 * <pre>
 * auditLogService.record()
 *     .action(AuditAction.APPROVE)
 *     .module(AuditModule.CAPA)
 *     .entity("Capa", capaId)
 *     .description("CAPA approved by QA Manager")
 *     .newValue(capaDto)
 *     .save();
 * </pre>
 */
@RequiredArgsConstructor
public class AuditBuilder {

    private final AuditLogService      service;
    private final AuditValueSerializer serializer;

    private final AuditLog.AuditLogBuilder inner = AuditLog.builder();

    public AuditBuilder action(AuditAction action) {
        inner.action(action);
        return this;
    }

    public AuditBuilder module(AuditModule module) {
        inner.module(module);
        return this;
    }

    public AuditBuilder entity(String entityType, Long entityId) {
        inner.entityType(entityType).entityId(entityId);
        return this;
    }

    public AuditBuilder entityType(String entityType) {
        inner.entityType(entityType);
        return this;
    }

    public AuditBuilder entityId(Long entityId) {
        inner.entityId(entityId);
        return this;
    }

    public AuditBuilder description(String description) {
        inner.description(description);
        return this;
    }

    public AuditBuilder oldValue(Object oldValue) {
        inner.oldValue(serializer.serialize(oldValue));
        return this;
    }

    public AuditBuilder newValue(Object newValue) {
        inner.newValue(serializer.serialize(newValue));
        return this;
    }

    /** Pre-serialized JSON (e.g. fetched from a snapshot store). */
    public AuditBuilder rawOldValue(String json) {
        inner.oldValue(json);
        return this;
    }

    public AuditBuilder rawNewValue(String json) {
        inner.newValue(json);
        return this;
    }

    public AuditBuilder outcome(AuditOutcome outcome) {
        inner.outcome(outcome);
        return this;
    }

    public AuditBuilder failure(Throwable ex) {
        inner.outcome(AuditOutcome.FAILURE)
             .errorType(ex.getClass().getName())
             .errorMessage(truncate(ex.getMessage(), 1000));
        return this;
    }

    public AuditBuilder userId(Long userId) {
        inner.userId(userId);
        return this;
    }

    public AuditBuilder username(String username) {
        inner.username(username);
        return this;
    }

    public AuditBuilder ipAddress(String ip) {
        inner.ipAddress(ip);
        return this;
    }

    public AuditBuilder correlationId(String correlationId) {
        inner.correlationId(correlationId);
        return this;
    }

    public AuditBuilder durationMs(long ms) {
        inner.durationMs(ms);
        return this;
    }

    /** Persists the log entry asynchronously. */
    public void save() {
        service.log(inner.build());
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
